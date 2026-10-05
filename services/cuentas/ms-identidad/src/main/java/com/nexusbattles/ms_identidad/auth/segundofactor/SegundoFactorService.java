package com.nexusbattles.ms_identidad.auth.segundofactor;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.segundofactor.CambioDeSegundoFactor.Cambio;
import com.nexusbattles.ms_identidad.auth.segundofactor.SegundoFactorRechazadoException.Motivo;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.DesactivarSegundoFactorRequest;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.EnrolamientoResponse;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.EstadoSegundoFactorResponse;
import com.nexusbattles.ms_identidad.auth.service.IntentosFallidosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * El segundo factor TOTP de la propia cuenta — HU-AUT-007 (RF-AUT-007).
 *
 * <p>Enrolar en dos tiempos: {@link #iniciarEnrolamiento} genera el secreto y
 * lo guarda cifrado pero aun no protege nada; {@link #activar} exige un codigo
 * de la aplicacion (prueba de que el secreto quedo bien guardado en ella) y
 * solo entonces el login lo pide. Asi nadie se queda fuera de su cuenta por un
 * secreto mal copiado.
 *
 * <p><b>Intentos fallidos.</b> Un codigo incorrecto en el login
 * ({@link #comprobarEnElAcceso}) o al desactivar cuenta como intento fallido
 * de la cuenta: la misma politica de RF-AUT-009 que la contrasena
 * ({@link IntentosFallidosService}), con el mismo bloqueo. Uno incorrecto al
 * CONFIRMAR el enrolamiento no cuenta: quien lo teclea ya tiene la sesion y
 * esta probando un secreto que acaba de generar el mismo; adivinar ahi no le
 * da nada que no tenga.
 */
@Service
public class SegundoFactorService {

    private static final Logger log = LoggerFactory.getLogger(SegundoFactorService.class);

    /** RFC 4226 §4: el secreto compartido deberia tener al menos 160 bits. */
    static final int BYTES_DEL_SECRETO = 20;

    private final SegundoFactorRepository segundos;
    private final CodigoDeRecuperacionRepository codigos;
    private final DesafioDeAccesoRepository desafios;
    private final CifradoDeSecretos cifrado;
    private final CodigosDeRecuperacion generador;
    private final PoliticaDeSegundoFactor politica;
    private final IntentosFallidosService intentosFallidos;
    private final ApplicationEventPublisher eventos;
    private final PasswordEncoder resumidor;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();

    @Autowired
    public SegundoFactorService(SegundoFactorRepository segundos,
                                CodigoDeRecuperacionRepository codigos,
                                DesafioDeAccesoRepository desafios,
                                CifradoDeSecretos cifrado,
                                CodigosDeRecuperacion generador,
                                PoliticaDeSegundoFactor politica,
                                IntentosFallidosService intentosFallidos,
                                ApplicationEventPublisher eventos) {
        this(segundos, codigos, desafios, cifrado, generador, politica, intentosFallidos, eventos,
                new BCryptPasswordEncoder(), Clock.systemDefaultZone());
    }

    /** Con resumidor y reloj explicitos: pruebas (BCrypt con pocas rondas, hora fija). */
    public SegundoFactorService(SegundoFactorRepository segundos,
                                CodigoDeRecuperacionRepository codigos,
                                DesafioDeAccesoRepository desafios,
                                CifradoDeSecretos cifrado,
                                CodigosDeRecuperacion generador,
                                PoliticaDeSegundoFactor politica,
                                IntentosFallidosService intentosFallidos,
                                ApplicationEventPublisher eventos,
                                PasswordEncoder resumidor,
                                Clock reloj) {
        this.segundos = segundos;
        this.codigos = codigos;
        this.desafios = desafios;
        this.cifrado = cifrado;
        this.generador = generador;
        this.politica = politica;
        this.intentosFallidos = intentosFallidos;
        this.eventos = eventos;
        this.resumidor = resumidor;
        this.reloj = reloj;
    }

    /** Los datos asociados de GCM: el secreto solo se descifra en la fila de su cuenta. */
    static String contextoDe(Long usuarioId) {
        return "usuario:" + usuarioId;
    }

    /** Como aparece la cuenta en la auditoria: su uid (ADR-002), o la clave interna si no lo tiene. */
    static String afectado(Usuario usuario) {
        return usuario.getPublicId() != null ? usuario.getPublicId().toString() : "usuario-" + usuario.getId();
    }

    // ------------------------------------------------------------------ estado

    @Transactional(readOnly = true)
    public EstadoSegundoFactorResponse estado(Usuario usuario) {
        Optional<SegundoFactor> guardado = segundos.findById(usuario.getId());
        boolean activo = guardado.map(SegundoFactor::isActivo).orElse(false);
        boolean pendiente = guardado.map(s -> !s.isActivo()).orElse(false);
        String activadoEn = activo && guardado.get().getActivadoEn() != null
                ? guardado.get().getActivadoEn().atZone(reloj.getZone()).toInstant().toString()
                : null;
        Long restantes = activo ? codigos.countByUsuarioIdAndUsadoEnIsNull(usuario.getId()) : null;
        return new EstadoSegundoFactorResponse(activo, politica.esObligatorioPara(rolDe(usuario)),
                cifrado.disponible(), pendiente, activadoEn, restantes);
    }

    // ------------------------------------------------------------ enrolamiento

    /**
     * Genera el secreto y lo deja pendiente de confirmar. Pedirlo otra vez
     * antes de confirmar reemplaza el pendiente.
     *
     * @throws SegundoFactorRechazadoException 409 {@code segundo-factor-ya-activo}
     *         o 503 {@code segundo-factor-no-disponible}
     */
    @Transactional
    public EnrolamientoResponse iniciarEnrolamiento(Usuario usuario) {
        if (segundos.existsByUsuarioIdAndActivoTrue(usuario.getId())) {
            throw yaActivo();
        }
        byte[] secreto = new byte[BYTES_DEL_SECRETO];
        azar.nextBytes(secreto);
        // Cifrar antes de tocar la base: sin clave, 503 y nada guardado.
        String guardado = cifrado.cifrar(secreto, contextoDe(usuario.getId()));
        LocalDateTime ahora = LocalDateTime.now(reloj);

        Optional<SegundoFactor> existente = segundos.bloquear(usuario.getId());
        if (existente.isPresent()) {
            if (existente.get().isActivo()) {
                throw yaActivo();
            }
            existente.get().reemplazarPendiente(guardado, ahora);
            segundos.save(existente.get());
        } else {
            segundos.save(new SegundoFactor(usuario.getId(), guardado, ahora));
        }

        String base32 = Base32.codificar(secreto);
        String cuenta = usuario.getEmail();
        return new EnrolamientoResponse(base32, uriOtpauth(politica.emisor(), cuenta, base32), politica.emisor(),
                cuenta, Totp.ALGORITMO, Totp.DIGITOS, Totp.PERIODO_SEGUNDOS);
    }

    /**
     * La URI del «Key Uri Format» que leen las aplicaciones:
     * {@code otpauth://totp/Emisor:cuenta?secret=...&issuer=Emisor&...}.
     * Etiqueta y emisor van codificados como componente de URI (espacio =
     * {@code %20}, no {@code +}).
     */
    static String uriOtpauth(String emisor, String cuenta, String secretoBase32) {
        return "otpauth://totp/" + componente(emisor) + ":" + componente(cuenta)
                + "?secret=" + secretoBase32
                + "&issuer=" + componente(emisor)
                + "&algorithm=" + Totp.ALGORITMO
                + "&digits=" + Totp.DIGITOS
                + "&period=" + Totp.PERIODO_SEGUNDOS;
    }

    private static String componente(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Confirma el secreto pendiente con un codigo de la aplicacion: el segundo
     * factor queda activo y se entregan los codigos de recuperacion (una sola
     * vez: aqui solo se guarda su resumen).
     *
     * @throws SegundoFactorRechazadoException 409 sin enrolamiento o ya activo,
     *         422 {@code codigo-segundo-factor-invalido}, 503 sin clave
     */
    @Transactional
    public List<String> activar(Usuario usuario, String codigo, String ip) {
        SegundoFactor guardado = segundos.bloquear(usuario.getId())
                .orElseThrow(() -> new SegundoFactorRechazadoException(Motivo.SIN_ENROLAMIENTO,
                        "Primero pide el secreto para tu aplicación de autenticación."));
        if (guardado.isActivo()) {
            throw yaActivo();
        }
        byte[] secreto = cifrado.descifrar(guardado.getSecretoCifrado(), contextoDe(usuario.getId()));
        OptionalLong paso = Totp.verificar(secreto, codigo, reloj.instant(), null);
        if (paso.isEmpty()) {
            throw new SegundoFactorRechazadoException(Motivo.CODIGO_INVALIDO,
                    "Ese código no coincide. Comprueba que la hora de tu teléfono sea automática y escribe el "
                            + "código que se ve ahora en tu aplicación.");
        }

        LocalDateTime ahora = LocalDateTime.now(reloj);
        guardado.activar(ahora, paso.getAsLong());
        segundos.save(guardado);

        codigos.borrarDe(usuario.getId());
        List<String> nuevos = generador.generar(politica.codigosDeRecuperacion());
        for (String nuevo : nuevos) {
            codigos.save(new CodigoDeRecuperacion(usuario.getId(),
                    resumidor.encode(CodigosDeRecuperacion.normalizar(nuevo)), ahora));
        }
        log.info("SEGUNDO_FACTOR_ACTIVADO usuarioId={}", usuario.getId());
        eventos.publishEvent(new CambioDeSegundoFactor(afectado(usuario), Cambio.ACTIVADO, null, ip));
        return nuevos;
    }

    // ------------------------------------------------------------ desactivacion

    /**
     * Quita el segundo factor: contrasena actual y codigo (de la aplicacion o
     * de recuperacion). Cada fallo cuenta como intento fallido de la cuenta.
     *
     * @throws SegundoFactorRechazadoException 400, 409 no activo, 423
     *         bloqueada, 422 contrasena o codigo incorrectos, 503 sin clave
     */
    @Transactional
    public void desactivar(Usuario usuario, DesactivarSegundoFactorRequest datos, String ip) {
        exigirExactamenteUno(datos.codigo(), datos.codigoRecuperacion());
        SegundoFactor guardado = segundos.findById(usuario.getId()).filter(SegundoFactor::isActivo)
                .orElseThrow(() -> new SegundoFactorRechazadoException(Motivo.NO_ACTIVO,
                        "Tu cuenta no tiene activada la verificación en dos pasos."));
        exigirNoBloqueada(usuario);
        if (!resumidor.matches(datos.passwordActual(), usuario.getPassword())) {
            // Se confirma aparte (REQUIRES_NEW): sobrevive a la excepcion que sigue.
            intentosFallidos.registrarIntentoFallido(usuario.getId());
            throw new SegundoFactorRechazadoException(Motivo.CONTRASENA_INCORRECTA,
                    "La contraseña actual es incorrecta.");
        }
        if (comprobar(usuario, guardado, datos.codigo(), datos.codigoRecuperacion()).isEmpty()) {
            intentosFallidos.registrarIntentoFallido(usuario.getId());
            throw new SegundoFactorRechazadoException(Motivo.CODIGO_INVALIDO,
                    "El código no es válido. Escribe el que se ve ahora en tu aplicación o uno de tus códigos "
                            + "de recuperación.");
        }
        segundos.borrarDe(usuario.getId());
        codigos.borrarDe(usuario.getId());
        desafios.borrarDe(usuario.getId());
        log.info("SEGUNDO_FACTOR_DESACTIVADO usuarioId={}", usuario.getId());
        eventos.publishEvent(new CambioDeSegundoFactor(afectado(usuario), Cambio.DESACTIVADO, null, ip));
    }

    // ------------------------------------------------------ en el acceso (login)

    /**
     * El segundo paso del login: el codigo de la aplicacion o uno de
     * recuperacion. Con la cuenta bloqueada no se compara nada (423); un fallo
     * cuenta como intento fallido (401).
     */
    @Transactional
    public ComprobacionDelSegundoFactor comprobarEnElAcceso(Usuario usuario, String codigo,
                                                            String codigoRecuperacion, String ip) {
        exigirExactamenteUno(codigo, codigoRecuperacion);
        exigirNoBloqueada(usuario);
        SegundoFactor guardado = segundos.findById(usuario.getId()).filter(SegundoFactor::isActivo)
                // Se desactivo entre los dos pasos: el desafio ya no tiene sentido.
                .orElseThrow(SegundoFactorRechazadoException::desafioInvalido);
        Optional<ComprobacionDelSegundoFactor> comprobacion = comprobar(usuario, guardado, codigo,
                codigoRecuperacion);
        if (comprobacion.isEmpty()) {
            intentosFallidos.registrarIntentoFallido(usuario.getId());
            log.info("SEGUNDO_FACTOR_FALLIDO usuarioId={}", usuario.getId());
            throw new SegundoFactorRechazadoException(Motivo.CODIGO_INVALIDO_EN_EL_ACCESO,
                    "El código no es válido. Escribe el que se ve ahora en tu aplicación o uno de tus códigos "
                            + "de recuperación.");
        }
        if (comprobacion.get().conRecuperacion()) {
            eventos.publishEvent(new CambioDeSegundoFactor(afectado(usuario), Cambio.RECUPERACION_USADA,
                    comprobacion.get().restantes(), ip));
        }
        return comprobacion.get();
    }

    // ------------------------------------------------------------------ piezas

    /** Vacio si el codigo no vale; nunca registra el intento (eso lo decide quien llama). */
    private Optional<ComprobacionDelSegundoFactor> comprobar(Usuario usuario, SegundoFactor guardado,
                                                            String codigo, String codigoRecuperacion) {
        if (hayValor(codigo)) {
            byte[] secreto = cifrado.descifrar(guardado.getSecretoCifrado(), contextoDe(usuario.getId()));
            OptionalLong paso = Totp.verificar(secreto, codigo, reloj.instant(), guardado.getUltimoPasoUsado());
            // El UPDATE condicionado es el que decide de verdad: de dos peticiones
            // con el mismo codigo a la vez, solo una anota el paso.
            if (paso.isEmpty() || segundos.registrarPaso(usuario.getId(), paso.getAsLong()) == 0) {
                return Optional.empty();
            }
            return Optional.of(ComprobacionDelSegundoFactor.conAplicacion());
        }
        String normalizado = CodigosDeRecuperacion.normalizar(codigoRecuperacion);
        if (!CodigosDeRecuperacion.pareceUnCodigo(normalizado)) {
            return Optional.empty();
        }
        for (CodigoDeRecuperacion candidato : codigos.findByUsuarioIdAndUsadoEnIsNull(usuario.getId())) {
            if (resumidor.matches(normalizado, candidato.getCodigoHash())) {
                if (codigos.marcarUsado(candidato.getId(), LocalDateTime.now(reloj)) == 0) {
                    return Optional.empty();
                }
                return Optional.of(ComprobacionDelSegundoFactor.conRecuperacion(
                        codigos.countByUsuarioIdAndUsadoEnIsNull(usuario.getId())));
            }
        }
        return Optional.empty();
    }

    private void exigirNoBloqueada(Usuario usuario) {
        if (usuario.getBloqueadoHasta() != null && LocalDateTime.now(reloj).isBefore(usuario.getBloqueadoHasta())) {
            throw new SegundoFactorRechazadoException(Motivo.CUENTA_BLOQUEADA,
                    "Cuenta bloqueada temporalmente por intentos fallidos. Intenta de nuevo más tarde.");
        }
    }

    private static void exigirExactamenteUno(String codigo, String codigoRecuperacion) {
        if (hayValor(codigo) == hayValor(codigoRecuperacion)) {
            throw new SegundoFactorRechazadoException(Motivo.DATOS_INVALIDOS,
                    "Escribe el código de tu aplicación o un código de recuperación (uno de los dos).");
        }
    }

    private static boolean hayValor(String texto) {
        return texto != null && !texto.isBlank();
    }

    private static String rolDe(Usuario usuario) {
        return usuario.getRol() == null ? null : usuario.getRol().getNombre();
    }

    private static SegundoFactorRechazadoException yaActivo() {
        return new SegundoFactorRechazadoException(Motivo.YA_ACTIVO,
                "La verificación en dos pasos ya está activa. Para cambiar de aplicación, desactívala primero.");
    }
}
