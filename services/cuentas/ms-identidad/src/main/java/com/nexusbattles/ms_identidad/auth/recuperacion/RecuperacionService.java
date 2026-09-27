package com.nexusbattles.ms_identidad.auth.recuperacion;

import com.nexusbattles.ms_identidad.auth.codigos.CodigoInvalidoException;
import com.nexusbattles.ms_identidad.auth.codigos.CodigosDeCorreo;
import com.nexusbattles.ms_identidad.auth.codigos.Comprobacion;
import com.nexusbattles.ms_identidad.auth.codigos.Comprobacion.Resultado;
import com.nexusbattles.ms_identidad.auth.codigos.ContrasenaRestablecida;
import com.nexusbattles.ms_identidad.auth.codigos.DemasiadosIntentosException;
import com.nexusbattles.ms_identidad.auth.codigos.IgualadorDeTiempo;
import com.nexusbattles.ms_identidad.auth.codigos.TipoCodigo;
import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.recuperacion.RecuperacionRechazadaException.Motivo;
import com.nexusbattles.ms_identidad.auth.validation.PasswordPolicyValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * «Olvide mi contrasena» endurecido (HU-COR-003, 7.1.1), y el canje de la
 * activacion de cuentas administrativas, que es el mismo gesto.
 *
 * <p>Tres pasos publicos:
 * <ol>
 *   <li>{@code solicitar}: responde SIEMPRE lo mismo. Si la cuenta existe y
 *       esta activa, y no se ha pasado el limite (60 s entre dos, 3 por hora),
 *       emite un codigo que anula los anteriores.</li>
 *   <li>{@code preguntas}: a cambio del codigo, las preguntas que la cuenta
 *       configuro. Sin codigo no se sabe ni si la cuenta existe.</li>
 *   <li>{@code confirmar}: codigo, respuestas (si la cuenta las configuro) y
 *       contrasena nueva. Exito: contrasena nueva, codigo usado, sesiones
 *       cerradas ({@code versionToken++}), aviso {@code cambio-clave} y
 *       auditoria.</li>
 * </ol>
 *
 * <p><b>Desviacion documentada del 7.1.1.</b> El documento pide codigo Y
 * respuestas. Mientras el PO no decida hacer obligatorias las preguntas
 * ({@code identidad.recuperacion.preguntas-obligatorias}, {@code false} por
 * omision, PROVISIONAL), se exigen solo si la cuenta las configuro: las
 * cuentas existentes no tienen ninguna y exigirlas las dejaria sin forma de
 * recuperarse. Con la propiedad en {@code true}, una cuenta sin preguntas no
 * puede completar la recuperacion por autoservicio (422).
 */
@Service
public class RecuperacionService {

    public static final String MENSAJE_SOLICITUD = "Si existe una cuenta asociada, recibirás instrucciones.";
    public static final String MENSAJE_CANJE = "Contraseña actualizada correctamente. Ya puedes iniciar sesión.";

    private final UsuarioRepository usuarios;
    private final CodigosDeCorreo codigos;
    private final IgualadorDeTiempo igualador;
    private final PreguntasDeSeguridadService preguntas;
    private final PasswordPolicyValidator politica;
    private final ApplicationEventPublisher eventos;
    private final boolean preguntasObligatorias;
    private final PasswordEncoder cifrador;
    private final Clock reloj;

    @Autowired
    public RecuperacionService(UsuarioRepository usuarios,
                               CodigosDeCorreo codigos,
                               IgualadorDeTiempo igualador,
                               PreguntasDeSeguridadService preguntas,
                               PasswordPolicyValidator politica,
                               ApplicationEventPublisher eventos,
                               @Value("${identidad.recuperacion.preguntas-obligatorias:false}")
                               boolean preguntasObligatorias) {
        this(usuarios, codigos, igualador, preguntas, politica, eventos, preguntasObligatorias,
                new BCryptPasswordEncoder(), Clock.systemDefaultZone());
    }

    /** Con cifrador y reloj explicitos: pruebas. */
    public RecuperacionService(UsuarioRepository usuarios,
                               CodigosDeCorreo codigos,
                               IgualadorDeTiempo igualador,
                               PreguntasDeSeguridadService preguntas,
                               PasswordPolicyValidator politica,
                               ApplicationEventPublisher eventos,
                               boolean preguntasObligatorias,
                               PasswordEncoder cifrador,
                               Clock reloj) {
        this.usuarios = usuarios;
        this.codigos = codigos;
        this.igualador = igualador;
        this.preguntas = preguntas;
        this.politica = politica;
        this.eventos = eventos;
        this.preguntasObligatorias = preguntasObligatorias;
        this.cifrador = cifrador;
        this.reloj = reloj;
    }

    /**
     * Pide un codigo. No lanza nada ni devuelve nada: quien llama responde
     * {@link #MENSAJE_SOLICITUD} pase lo que pase aqui. Los caminos que no
     * emiten pagan el mismo BCrypt que el que si ({@link IgualadorDeTiempo}).
     */
    @Transactional
    public void solicitar(String email) {
        // Bloqueada la fila de la cuenta, dos solicitudes a la vez se turnan y
        // la segunda ve el codigo de la primera al contar: el limite es exacto.
        Optional<Usuario> cuenta = usuarios.buscarPorCorreo(email)
                .filter(this::puedeRecuperar)
                .flatMap(usuario -> usuarios.bloquear(usuario.getId()));
        if (cuenta.isEmpty() || !codigos.admiteOtroEnvio(cuenta.get().getId(), TipoCodigo.RESTABLECIMIENTO)) {
            igualador.resumir();
            return;
        }
        codigos.emitir(cuenta.get(), TipoCodigo.RESTABLECIMIENTO);
    }

    /**
     * Las preguntas de la cuenta, a cambio del codigo. Un codigo incorrecto
     * cuenta como intento fallido; uno correcto NO se consume (se canjea en
     * {@link #confirmar}).
     */
    @Transactional(readOnly = true)
    public PreguntasDeRecuperacion preguntas(String email, String codigo) {
        Usuario usuario = cuentaOCodigoInvalido(email, codigo);
        Comprobacion valida = codigos.comprobar(usuario.getId(), TipoCodigo.RESTABLECIMIENTO, codigo).exigirValida();
        if (valida.tipo() == TipoCodigo.ACTIVACION) {
            // Una cuenta que se esta activando no ha podido configurar nada.
            return PreguntasDeRecuperacion.ninguna();
        }
        return preguntas.deLaCuenta(usuario.getId());
    }

    /**
     * Canjea el codigo y fija la contrasena nueva.
     *
     * <p>Orden: codigo (cuenta intento si falla) -> politica de la contrasena
     * (no cuenta intento ni quema el codigo: la persona corrige y reintenta)
     * -> respuestas (si fallan, cuentan como intento del codigo) -> canje.
     */
    @Transactional
    public void confirmar(String email, String codigo, List<RespuestaDeSeguridad> respuestas,
                          String nuevaPassword, String ip) {
        Usuario usuario = cuentaOCodigoInvalido(email, codigo);
        Comprobacion valida = codigos.comprobar(usuario.getId(), TipoCodigo.RESTABLECIMIENTO, codigo).exigirValida();

        List<String> faltan = politica.reglasQueFaltan(nuevaPassword);
        if (!faltan.isEmpty()) {
            throw new RecuperacionRechazadaException(Motivo.POLITICA,
                    "La contraseña nueva " + String.join("; ", faltan) + ".");
        }

        if (exigenPreguntas(usuario, valida.tipo()) && !preguntas.respuestasCorrectas(usuario.getId(), respuestas)) {
            if (codigos.registrarFallo(valida.codigoId()).resultado() == Resultado.DEMASIADOS_INTENTOS) {
                throw new DemasiadosIntentosException();
            }
            throw new RecuperacionRechazadaException(Motivo.RESPUESTAS_INCORRECTAS,
                    preguntas.tieneConfiguradas(usuario.getId())
                            ? "Las respuestas de seguridad no coinciden."
                            : "La cuenta no tiene preguntas de seguridad configuradas; pide ayuda a un administrador.");
        }

        if (!codigos.marcarUsado(valida.codigoId())) {
            throw new CodigoInvalidoException();
        }
        usuario.setPassword(cifrador.encode(nuevaPassword));
        // Cierra todas las sesiones abiertas: si quien restablece no es quien
        // tenia la sesion, la sesion ajena termina aqui.
        usuario.setVersionToken(usuario.getVersionToken() + 1);
        // Demostrar el buzon y fijar la contrasena desbloquea el login.
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        if (EstadoCuenta.INACTIVO.equals(usuario.getEstado())) {
            // Activacion de una cuenta administrativa: el mismo canje la deja lista.
            usuario.setEstado(EstadoCuenta.ACTIVO);
        }
        usuarios.save(usuario);

        eventos.publishEvent(new ContrasenaRestablecida(valida.codigoId(), valida.tipo(), usuario.getPublicId(),
                usuario.getId(), usuario.getEmail(), usuario.getApodo(), ip));
    }

    private Usuario cuentaOCodigoInvalido(String email, String codigo) {
        Optional<Usuario> usuario = usuarios.buscarPorCorreo(email);
        if (usuario.isEmpty()) {
            igualador.comparar(codigo);
            throw new CodigoInvalidoException();
        }
        return usuario.get();
    }

    /**
     * Solo una cuenta ACTIVA recibe codigos de restablecimiento por
     * autoservicio. Una suspension ya vencida cuenta como activa (el login la
     * levanta al entrar, y sin contrasena la persona no podria entrar a
     * levantarla). Pendientes de verificar, baneadas, suspendidas vigentes y
     * cuentas administrativas sin activar, no.
     */
    private boolean puedeRecuperar(Usuario usuario) {
        String estado = usuario.getEstado();
        if (EstadoCuenta.ACTIVO.equals(estado)) {
            return true;
        }
        return EstadoCuenta.esSuspendido(estado)
                && (usuario.getSuspendidoHasta() == null
                    || !LocalDateTime.now(reloj).isBefore(usuario.getSuspendidoHasta()));
    }

    private boolean exigenPreguntas(Usuario usuario, TipoCodigo tipo) {
        if (tipo == TipoCodigo.ACTIVACION) {
            return false;
        }
        return preguntasObligatorias || preguntas.tieneConfiguradas(usuario.getId());
    }
}
