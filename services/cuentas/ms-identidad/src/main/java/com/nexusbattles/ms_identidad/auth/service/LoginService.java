package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.codigos.IgualadorDeTiempo;
import com.nexusbattles.ms_identidad.auth.correo.CorreoClient;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoAvisoAccesoRequest;
import com.nexusbattles.ms_identidad.auth.dto.LoginRequest;
import com.nexusbattles.ms_identidad.auth.dto.LoginResponse;
import com.nexusbattles.ms_identidad.auth.exception.CredencialesInvalidasException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaBaneadaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaBloqueadaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaInactivaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaNoVerificadaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaSuspendidaException;
import com.nexusbattles.ms_identidad.auth.model.DispositivoConocido;
import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.DispositivoConocidoRepository;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.onboarding.auditoria.AuditoriaDeCuenta;
import com.nexusbattles.ms_identidad.onboarding.service.OnboardingService;
import com.nexusbattles.ms_identidad.sanciones.ProyeccionDeSancionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Inicio de sesion (HU-AUT-001, RF-AUT-009/010).
 *
 * <p><b>B1: la contrasena se comprueba ANTES que el estado de la cuenta.</b>
 * Hasta aqui una cuenta baneada, suspendida o inactiva respondia su 403 con
 * cualquier contrasena: el estado de una cuenta se le revelaba a quien solo
 * conocia el correo. Ahora, con la contrasena incorrecta, el 401 generico de
 * siempre; con la correcta, el motivo exacto:
 * <ul>
 *   <li>{@code PENDIENTE_VERIFICACION} -> 403 {@code cuenta-no-verificada} (B1);</li>
 *   <li>suspension vigente -> 403 {@code cuenta-suspendida}, con su fin;</li>
 *   <li>suspension vencida -> entra, y la proyeccion se limpia;</li>
 *   <li>{@code BANEADO} -> 403 {@code cuenta-baneada}.</li>
 * </ul>
 * El bloqueo por intentos fallidos (423) sigue mirandose antes de comparar
 * la contrasena: mientras dura, no se evalua ninguna.
 *
 * <p>Un correo que no existe paga el mismo BCrypt que uno que si
 * ({@link IgualadorDeTiempo}): el tiempo tampoco dice que correos hay.
 */
@Service
public class LoginService {

    // TODO [INTEGRACIÓN FUTURA]: reemplazar este logger por una llamada real
    // al microservicio de auditoría (ms-cumplimiento, Juan Diego) cuando
    // publique su contrato. Por ahora queda registrado localmente.
    private static final Logger auditLog = LoggerFactory.getLogger("AUDITORIA_LOGIN");

    private final UsuarioRepository usuarioRepository;
    private final DispositivoConocidoRepository dispositivoConocidoRepository;
    private final IntentosFallidosService intentosFallidosService;
    private final CorreoClient correoClient;
    private final JwtService jwtService;
    private final AuditoriaLoginClient auditoriaLoginClient;
    private final AuditoriaDeCuenta auditoriaDeCuenta;
    private final OnboardingService onboardingService;
    private final IgualadorDeTiempo igualador;
    private final ProyeccionDeSancionService proyecciones;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // R17 — inyeccion por constructor (Sonar S6813): antes eran campos
    // @Autowired, y cada dependencia nueva sumaba un aviso.
    public LoginService(UsuarioRepository usuarioRepository,
                        DispositivoConocidoRepository dispositivoConocidoRepository,
                        IntentosFallidosService intentosFallidosService,
                        CorreoClient correoClient,
                        JwtService jwtService,
                        AuditoriaLoginClient auditoriaLoginClient,
                        AuditoriaDeCuenta auditoriaDeCuenta,
                        OnboardingService onboardingService,
                        IgualadorDeTiempo igualador,
                        ProyeccionDeSancionService proyecciones) {
        this.usuarioRepository = usuarioRepository;
        this.dispositivoConocidoRepository = dispositivoConocidoRepository;
        this.intentosFallidosService = intentosFallidosService;
        this.correoClient = correoClient;
        this.jwtService = jwtService;
        this.auditoriaLoginClient = auditoriaLoginClient;
        this.auditoriaDeCuenta = auditoriaDeCuenta;
        this.onboardingService = onboardingService;
        this.igualador = igualador;
        this.proyecciones = proyecciones;
    }

    @Transactional
    public LoginResponse iniciarSesion(LoginRequest datos, String direccionIp, String userAgent) {

        // R17 — mismo identificador que el registro: el correo, sin espacios
        // y sin importar las mayusculas con las que se teclee (el registro lo
        // guarda en minusculas; las cuentas anteriores se encuentran igual).
        Optional<Usuario> encontrada = usuarioRepository.buscarPorCorreo(datos.getEmail());
        if (encontrada.isEmpty()) {
            igualador.comparar(datos.getPassword());
            throw credencialesInvalidas(datos.getEmail(), direccionIp);
        }
        Usuario usuario = encontrada.get();

        // --- Bloqueo por intentos fallidos (RF-AUT-009) ---
        if (usuario.getBloqueadoHasta() != null && LocalDateTime.now().isBefore(usuario.getBloqueadoHasta())) {
            long minutosRestantes = ChronoUnit.MINUTES.between(LocalDateTime.now(), usuario.getBloqueadoHasta());
            auditLog.info("LOGIN_RECHAZADO usuarioId={} ip={} motivo=BLOQUEADA restante={}min",
                usuario.getId(), direccionIp, minutosRestantes);
            throw new CuentaBloqueadaException(
                "Cuenta bloqueada temporalmente por intentos fallidos. Intenta de nuevo en "
                    + minutosRestantes + " minutos.");
        }

        // --- Verificación de contraseña (antes que el estado: B1) ---
        if (!passwordEncoder.matches(datos.getPassword(), usuario.getPassword())) {
            intentosFallidosService.registrarIntentoFallido(usuario.getId());
            throw credencialesInvalidas(datos.getEmail(), direccionIp);
        }

        // --- Estado de la cuenta: solo quien sabe la contraseña llega aquí ---
        exigirQuePuedaEntrar(usuario, direccionIp);

        // --- Login exitoso: resetear contadores ---
        boolean primerAcceso = usuario.getUltimoAcceso() == null;
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        // Sello de ultima entrada: solo en un acceso correcto, nunca en uno
        // fallido. Es lo que la consola administrativa muestra como «ultima
        // entrada»; sin esto la unica alternativa era deducirla de la
        // auditoria o mostrar una fecha que nadie habia escrito.
        usuario.setUltimoAcceso(LocalDateTime.now());
        usuarioRepository.save(usuario);

        // --- Huella de dispositivo/ubicación (RF-AUT-010) ---
        String huella = calcularHuella(userAgent, direccionIp);
        boolean dispositivoNuevo = registrarOVerificarDispositivo(usuario, huella);

        if (dispositivoNuevo) {
            // Integración real con el módulo de correo de Santiago Anaya
            // (contracts/openapi/correo.yaml). Protegida con Resilience4j:
            // si el servicio de correo falla, el login se completa igual.
            String fechaHoraIso = OffsetDateTime.now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            correoClient.enviarAvisoAcceso(new CorreoAvisoAccesoRequest(
                usuario.getEmail(), usuario.getApodo(), direccionIp, fechaHoraIso
            ));

            auditLog.info("DISPOSITIVO_NUEVO usuarioId={} ip={}", usuario.getId(), direccionIp);
        }

        auditLog.info("LOGIN_EXITOSO usuarioId={} ip={}", usuario.getId(), direccionIp);

        // Token JWT firmado, incluyendo la versión vigente (HU-RBAC-003) —
        // reemplaza la confianza ciega en X-User-Role. El publicId viaja como
        // claim `uid` para que otros servicios referencien al usuario sin
        // depender del apodo, que es mutable.
        String token = jwtService.generarToken(
            usuario.getApodo(), usuario.getRol().getNombre(), usuario.getVersionToken(),
            usuario.getPublicId());

        // R17 — primer acceso a la auditoria, y si el alta del jugador quedo a
        // medias (un servicio caido cuando se registro), se relanza ahora sin
        // esperar al reintento programado. Ninguna de las dos retrasa el login.
        // B1: aqui solo llega una cuenta ACTIVA; una pendiente de verificar ya
        // salio con su 403 y su alta no arranca hasta confirmar el correo.
        if (primerAcceso) {
            auditoriaDeCuenta.primerAcceso(usuario.getPublicId(), direccionIp);
        }
        onboardingService.reanudarSiHaceFalta(usuario.getPublicId());

        return new LoginResponse(
            usuario.getId(),
            usuario.getApodo(),
            usuario.getEmail(),
            usuario.getRol().getNombre(),
            dispositivoNuevo,
            token,
            usuario.getPublicId() == null ? null : usuario.getPublicId().toString(),
            onboardingService.listo(usuario.getPublicId())
        );
    }

    /**
     * El estado de la cuenta, con la contraseña ya comprobada. El baneo va
     * primero (no vence nunca); una suspension vencida se limpia y, si la
     * cuenta nunca confirmo su correo, vuelve a quedar pendiente.
     */
    private void exigirQuePuedaEntrar(Usuario usuario, String direccionIp) {
        String estado = usuario.getEstado();
        if (EstadoCuenta.esBaneado(estado)) {
            rechazo(usuario, direccionIp, "BANEADA");
            throw new CuentaBaneadaException("Esta cuenta ha sido baneada permanentemente.");
        }
        if (EstadoCuenta.esSuspendido(estado)) {
            LocalDateTime hasta = usuario.getSuspendidoHasta();
            if (hasta != null && LocalDateTime.now().isBefore(hasta)) {
                long minutosRestantes = ChronoUnit.MINUTES.between(LocalDateTime.now(), hasta);
                rechazo(usuario, direccionIp, "SUSPENDIDA");
                throw new CuentaSuspendidaException(
                    "Cuenta suspendida. Tiempo restante: " + minutosRestantes + " minutos.",
                    hasta.atZone(ZoneId.systemDefault()).toOffsetDateTime());
            }
            estado = proyecciones.levantarSiVencida(usuario);
        }
        if (EstadoCuenta.PENDIENTE_VERIFICACION.equals(estado)) {
            rechazo(usuario, direccionIp, "NO_VERIFICADA");
            throw new CuentaNoVerificadaException();
        }
        if (EstadoCuenta.INACTIVO.equals(estado)) {
            rechazo(usuario, direccionIp, "INACTIVO");
            throw new CuentaInactivaException(
                "Esta cuenta aún no ha sido activada. Revisa tu correo para completar el proceso.");
        }
    }

    private static void rechazo(Usuario usuario, String direccionIp, String motivo) {
        auditLog.info("LOGIN_RECHAZADO usuarioId={} ip={} motivo={}", usuario.getId(), direccionIp, motivo);
    }

    private boolean registrarOVerificarDispositivo(Usuario usuario, String huella) {
        Optional<DispositivoConocido> existente =
            dispositivoConocidoRepository.findByUsuarioAndHuella(usuario, huella);

        if (existente.isPresent()) {
            return false;
        }

        dispositivoConocidoRepository.save(new DispositivoConocido(usuario, huella));
        return true;
    }

    private String calcularHuella(String userAgent, String ip) {
        try {
            String base = (userAgent == null ? "" : userAgent) + "|" + (ip == null ? "" : ip);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return userAgent + "|" + ip;
        }
    }

    private CredencialesInvalidasException credencialesInvalidas(String email, String ip) {

        auditLog.info(
            "LOGIN_FALLIDO email={} ip={} motivo=CREDENCIALES_INVALIDAS_ENTORNO",
            email,
            ip
        );

        auditoriaLoginClient.registrarLoginFallido(email, ip);

        return new CredencialesInvalidasException(
            "Correo o contraseña incorrectos."
        );
    }
}
