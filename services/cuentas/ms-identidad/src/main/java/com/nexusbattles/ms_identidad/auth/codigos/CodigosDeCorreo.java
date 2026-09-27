package com.nexusbattles.ms_identidad.auth.codigos;

import com.nexusbattles.ms_identidad.auth.model.TokenCredencial;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.TokenCredencialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Los codigos de un solo uso que viajan al correo (B1): verificacion del
 * correo, activacion de cuentas administrativas y restablecimiento.
 *
 * <p>Las reglas, todas en un sitio para que los tres flujos las cumplan
 * igual:
 * <ol>
 *   <li><b>Se guarda el resumen, no el codigo</b> (BCrypt). El valor en claro
 *       solo existe en memoria hasta que {@link CorreosDeCuenta} lo entrega.</li>
 *   <li><b>Uno vigente por familia</b>: emitir uno nuevo anula los anteriores
 *       (reenviar invalida el codigo viejo).</li>
 *   <li><b>Vigencia</b> por tipo ({@link PoliticaDeCodigos}).</li>
 *   <li><b>Intentos contados en el codigo</b>: cada fallo suma y al llegar al
 *       maximo el codigo se anula (429). El fallo se confirma en su propia
 *       transaccion para sobrevivir a la excepcion que responde al cliente,
 *       y la fila se bloquea mientras se compara: peticiones en paralelo no
 *       consiguen mas intentos que los permitidos.</li>
 *   <li><b>Un solo uso</b>: marcarlo usado es un UPDATE condicionado; de dos
 *       canjes simultaneos solo uno lo consigue.</li>
 *   <li><b>Frecuencia de envio</b> calculada en la base (no hay Redis).</li>
 * </ol>
 */
@Service
public class CodigosDeCorreo {

    private final TokenCredencialRepository codigos;
    private final PoliticaDeCodigos politica;
    private final GeneradorDeCodigos generador;
    private final IgualadorDeTiempo igualador;
    private final ApplicationEventPublisher eventos;
    private final PasswordEncoder resumidor;
    private final Clock reloj;

    @Autowired
    public CodigosDeCorreo(TokenCredencialRepository codigos,
                           PoliticaDeCodigos politica,
                           GeneradorDeCodigos generador,
                           IgualadorDeTiempo igualador,
                           ApplicationEventPublisher eventos) {
        this(codigos, politica, generador, igualador, eventos, new BCryptPasswordEncoder(), Clock.systemDefaultZone());
    }

    /** Con cifrador y reloj explicitos: pruebas (BCrypt con pocas rondas, hora fija). */
    public CodigosDeCorreo(TokenCredencialRepository codigos,
                           PoliticaDeCodigos politica,
                           GeneradorDeCodigos generador,
                           IgualadorDeTiempo igualador,
                           ApplicationEventPublisher eventos,
                           PasswordEncoder resumidor,
                           Clock reloj) {
        this.codigos = codigos;
        this.politica = politica;
        this.generador = generador;
        this.igualador = igualador;
        this.eventos = eventos;
        this.resumidor = resumidor;
        this.reloj = reloj;
    }

    /**
     * Emite un codigo nuevo y anula los vigentes de su familia.
     *
     * <p>{@code MANDATORY}: corre dentro de la transaccion de quien lo pide
     * (el alta, el reenvio, la solicitud de restablecimiento...) y el correo
     * sale solo si esa transaccion se confirma. Un alta que se deshace no deja
     * un codigo enviado a nadie.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CodigoEmitido emitir(Usuario usuario, TipoCodigo tipo) {
        LocalDateTime ahora = ahora();
        codigos.anularVigentes(usuario.getId(), tipo.familiaComoTexto(), ahora);

        String codigo = generador.nuevo();
        int minutos = politica.minutosVigencia(tipo);
        TokenCredencial fila = codigos.save(new TokenCredencial(
                usuario, tipo.name(), resumidor.encode(codigo), ahora, ahora.plusMinutes(minutos)));

        eventos.publishEvent(new CodigoParaEnviar(fila.getId(), tipo, usuario.getEmail(), usuario.getApodo(),
                codigo, minutos));
        return new CodigoEmitido(fila.getId(), tipo, minutos);
    }

    /**
     * Si a esta cuenta se le puede enviar otro codigo de este tipo ahora:
     * ha pasado la pausa minima desde el ultimo y no se ha llegado al maximo
     * de la ultima hora. Quien pregunta decide que hacer si no (las rutas
     * publicas responden lo mismo y no envian nada).
     *
     * <p>Exacto solo si quien llama tiene bloqueada la cuenta
     * ({@code UsuarioRepository.bloquear}): si no, dos peticiones a la vez
     * pasan las dos la comprobacion.
     */
    public boolean admiteOtroEnvio(Long usuarioId, TipoCodigo tipo) {
        LocalDateTime ahora = ahora();
        Optional<LocalDateTime> ultimo = codigos.ultimaEmision(usuarioId, tipo.name());
        if (ultimo.isPresent() && ultimo.get().plus(politica.pausaEntreEnvios(tipo)).isAfter(ahora)) {
            return false;
        }
        return codigos.contarEmitidosDesde(usuarioId, tipo.name(), ahora.minusHours(1)) < politica.maximoPorHora(tipo);
    }

    /**
     * Compara {@code entrada} con el ULTIMO codigo de la familia de
     * {@code tipo} de esa cuenta; un fallo cuenta contra ese codigo.
     *
     * <p>{@code REQUIRES_NEW}: el intento fallido se confirma aunque quien
     * llama responda con una excepcion (y deshaga lo suyo). La fila queda
     * bloqueada hasta entonces, asi que dos intentos a la vez se turnan.
     *
     * <p>Todos los caminos pagan exactamente un BCrypt, tambien los que no
     * tienen con que comparar: el tiempo no distingue «no hay codigo» de
     * «codigo incorrecto».
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Comprobacion comprobar(Long usuarioId, TipoCodigo tipo, String entrada) {
        Optional<TokenCredencial> ultimo =
                codigos.findFirstByUsuario_IdAndTipoInOrderByIdDesc(usuarioId, tipo.familiaComoTexto());
        if (ultimo.isEmpty()) {
            igualador.comparar(entrada);
            return Comprobacion.invalido();
        }
        TokenCredencial fila = ultimo.get();
        LocalDateTime ahora = ahora();
        if (fila.isUsado() || fila.getCodigoHash() == null || ahora.isAfter(fila.getFechaExpiracion())) {
            igualador.comparar(entrada);
            return Comprobacion.invalido();
        }
        if (fila.getAnuladoEn() != null) {
            igualador.comparar(entrada);
            return fila.getIntentosFallidos() >= politica.intentosMaximos()
                    ? Comprobacion.demasiadosIntentos() : Comprobacion.invalido();
        }
        if (resumidor.matches(GeneradorDeCodigos.normalizar(entrada), fila.getCodigoHash())) {
            return Comprobacion.valido(fila.getId(), TipoCodigo.valueOf(fila.getTipo()));
        }
        return contarFallo(fila, ahora);
    }

    /**
     * Un fallo que no es del codigo sino de lo que lo acompana (las
     * respuestas de seguridad del restablecimiento): cuenta contra el mismo
     * codigo, con las mismas reglas.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Comprobacion registrarFallo(Long codigoId) {
        Optional<TokenCredencial> bloqueada = codigos.bloquear(codigoId);
        if (bloqueada.isEmpty() || bloqueada.get().isUsado()) {
            return Comprobacion.invalido();
        }
        TokenCredencial fila = bloqueada.get();
        if (fila.getAnuladoEn() != null) {
            return fila.getIntentosFallidos() >= politica.intentosMaximos()
                    ? Comprobacion.demasiadosIntentos() : Comprobacion.invalido();
        }
        return contarFallo(fila, ahora());
    }

    /**
     * Canjea el codigo. {@code false} si otro lo canjeo antes (o se anulo
     * entre medias): quien llama responde como a un codigo invalido.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean marcarUsado(Long codigoId) {
        return codigos.marcarUsado(codigoId, ahora()) == 1;
    }

    /**
     * Si esta cuenta nacio por autorregistro y nunca confirmo su correo: tiene
     * codigos de verificacion y ninguno se uso. Las cuentas anteriores a B1 no
     * tienen ninguno y no cuentan como pendientes.
     */
    public boolean nuncaVerificada(Long usuarioId) {
        String verificacion = TipoCodigo.VERIFICACION.name();
        return codigos.existsByUsuario_IdAndTipo(usuarioId, verificacion)
                && !codigos.existsByUsuario_IdAndTipoAndUsadoTrue(usuarioId, verificacion);
    }

    private Comprobacion contarFallo(TokenCredencial fila, LocalDateTime ahora) {
        fila.setIntentosFallidos(fila.getIntentosFallidos() + 1);
        boolean agotado = fila.getIntentosFallidos() >= politica.intentosMaximos();
        if (agotado) {
            fila.setAnuladoEn(ahora);
        }
        codigos.save(fila);
        return agotado ? Comprobacion.demasiadosIntentos() : Comprobacion.invalido();
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }
}
