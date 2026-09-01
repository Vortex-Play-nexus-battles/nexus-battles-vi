package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.correo.CorreoClient;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoAvisoAccesoRequest;
import com.nexusbattles.ms_identidad.auth.dto.LoginRequest;
import com.nexusbattles.ms_identidad.auth.dto.LoginResponse;
import com.nexusbattles.ms_identidad.auth.exception.CredencialesInvalidasException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaBaneadaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaBloqueadaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaInactivaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaSuspendidaException;
import com.nexusbattles.ms_identidad.auth.model.DispositivoConocido;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.DispositivoConocidoRepository;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class LoginService {

    // TODO [INTEGRACIÓN FUTURA]: reemplazar este logger por una llamada real
    // al microservicio de auditoría (ms-cumplimiento, Juan Diego) cuando
    // publique su contrato. Por ahora queda registrado localmente.
    private static final Logger auditLog = LoggerFactory.getLogger("AUDITORIA_LOGIN");

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private DispositivoConocidoRepository dispositivoConocidoRepository;

    @Autowired
    private IntentosFallidosService intentosFallidosService;

    @Autowired
    private CorreoClient correoClient;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public LoginResponse iniciarSesion(LoginRequest datos, String direccionIp, String userAgent) {

        Usuario usuario = usuarioRepository.findByEmail(datos.getEmail())
            .orElseThrow(() -> credencialesInvalidas(datos.getEmail(), direccionIp));

        // --- Estado de la cuenta ---
        if ("BANEADA".equals(usuario.getEstado())) {
            auditLog.info("LOGIN_RECHAZADO email={} ip={} motivo=BANEADA", datos.getEmail(), direccionIp);
            throw new CuentaBaneadaException("Esta cuenta ha sido baneada permanentemente.");
        }

        if ("INACTIVO".equals(usuario.getEstado())) {
            auditLog.info("LOGIN_RECHAZADO email={} ip={} motivo=INACTIVO", datos.getEmail(), direccionIp);
            throw new CuentaInactivaException(
                "Esta cuenta aún no ha sido activada. Revisa tu correo para completar el proceso.");
        }

        if ("SUSPENDIDA".equals(usuario.getEstado())) {
            LocalDateTime hasta = usuario.getSuspendidoHasta();
            if (hasta != null && LocalDateTime.now().isBefore(hasta)) {
                long minutosRestantes = ChronoUnit.MINUTES.between(LocalDateTime.now(), hasta);
                auditLog.info("LOGIN_RECHAZADO email={} ip={} motivo=SUSPENDIDA restante={}min",
                    datos.getEmail(), direccionIp, minutosRestantes);
                throw new CuentaSuspendidaException(
                    "Cuenta suspendida. Tiempo restante: " + minutosRestantes + " minutos.");
            }
        }

        // --- Bloqueo por intentos fallidos (RF-AUT-009) ---
        if (usuario.getBloqueadoHasta() != null && LocalDateTime.now().isBefore(usuario.getBloqueadoHasta())) {
            long minutosRestantes = ChronoUnit.MINUTES.between(LocalDateTime.now(), usuario.getBloqueadoHasta());
            auditLog.info("LOGIN_RECHAZADO email={} ip={} motivo=BLOQUEADA restante={}min",
                datos.getEmail(), direccionIp, minutosRestantes);
            throw new CuentaBloqueadaException(
                "Cuenta bloqueada temporalmente por intentos fallidos. Intenta de nuevo en "
                    + minutosRestantes + " minutos.");
        }

        // --- Verificación de contraseña ---
        if (!passwordEncoder.matches(datos.getPassword(), usuario.getPassword())) {
            intentosFallidosService.registrarIntentoFallido(usuario.getId());
            auditLog.info("LOGIN_FALLIDO email={} ip={}", datos.getEmail(), direccionIp);
            throw credencialesInvalidas(datos.getEmail(), direccionIp);
        }

        // --- Login exitoso: resetear contadores ---
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
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

        auditLog.info("LOGIN_EXITOSO email={} ip={}", datos.getEmail(), direccionIp);

        return new LoginResponse(
            usuario.getId(),
            usuario.getApodo(),
            usuario.getEmail(),
            usuario.getRol().getNombre(),
            dispositivoNuevo
        );
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
        auditLog.info("LOGIN_FALLIDO email={} ip={} motivo=CREDENCIALES_INVALIDAS", email, ip);
        return new CredencialesInvalidasException("Correo o contraseña incorrectos.");
    }
}
