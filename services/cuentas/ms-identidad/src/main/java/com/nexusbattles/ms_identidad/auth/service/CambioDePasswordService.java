package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaClient;
import com.nexusbattles.ms_identidad.auth.correo.CorreoClient;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoCambioClaveRequest;
import com.nexusbattles.ms_identidad.auth.dto.CambiarPasswordRequest;
import com.nexusbattles.ms_identidad.auth.dto.CambioDePasswordResponse;
import com.nexusbattles.ms_identidad.auth.exception.CambioDePasswordRechazadoException;
import com.nexusbattles.ms_identidad.auth.exception.CambioDePasswordRechazadoException.Motivo;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.validation.PasswordPolicyValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cambio de contraseña del usuario autenticado — HU-AUT-006 (RF-AUT-006).
 *
 * <p>Orden de las comprobaciones, y por qué ese orden:
 * <ol>
 *   <li>Cuenta bloqueada por intentos (RF-AUT-009): antes de mirar nada. Un
 *       cambio de contraseña con la actual mal es, a efectos de fuerza bruta,
 *       lo mismo que un login fallido; si ya está bloqueada, ni se compara.</li>
 *   <li>La actual coincide (CA-02). Si no, se registra intento fallido y se
 *       responde solo «contraseña actual incorrecta». Va ANTES que la política
 *       para no darle a quien no conoce la actual ninguna pista sobre la
 *       nueva.</li>
 *   <li>Confirmación coincide, la nueva cumple la política y no es la
 *       anterior (CA-03), con el motivo exacto en cada caso.</li>
 *   <li>Se guarda con bcrypt, se sube {@code versionToken} para que caduquen
 *       las demás sesiones (CA-04, misma mecánica que HU-RBAC-003) y se emite
 *       un token nuevo para esta.</li>
 *   <li>Correo de aviso (CA-01) y auditoría: los dos fuera de la transacción
 *       lógica del cambio y sin poder deshacerlo. Si el correo no sale queda
 *       en bitácora; si la auditoría no responde, también — es el usuario
 *       cambiando SU contraseña, no una acción administrativa que haya que
 *       frenar.</li>
 * </ol>
 */
@Service
public class CambioDePasswordService {

    private static final Logger log = LoggerFactory.getLogger(CambioDePasswordService.class);

    static final String TIPO_AUDITORIA = "ACTUALIZACION";
    static final String MOTIVO_AUDITORIA = "Cambio de contraseña por el propio usuario (HU-AUT-006)";

    private final UsuarioRepository usuarioRepository;
    private final PasswordPolicyValidator politica;
    private final IntentosFallidosService intentosFallidos;
    private final JwtService jwtService;
    private final CorreoClient correoClient;
    private final AuditoriaClient auditoriaClient;
    private final PasswordEncoder passwordEncoder;
    private final Clock reloj;

    // Dos constructores publicos: Spring necesita saber cual es el suyo.
    @org.springframework.beans.factory.annotation.Autowired
    public CambioDePasswordService(UsuarioRepository usuarioRepository,
                                   PasswordPolicyValidator politica,
                                   IntentosFallidosService intentosFallidos,
                                   JwtService jwtService,
                                   CorreoClient correoClient,
                                   AuditoriaClient auditoriaClient) {
        this(usuarioRepository, politica, intentosFallidos, jwtService, correoClient, auditoriaClient,
                new BCryptPasswordEncoder(), Clock.systemDefaultZone());
    }

    /** Con codificador y reloj inyectables: para las pruebas (bcrypt con pocas rondas, hora fija). */
    public CambioDePasswordService(UsuarioRepository usuarioRepository,
                                   PasswordPolicyValidator politica,
                                   IntentosFallidosService intentosFallidos,
                                   JwtService jwtService,
                                   CorreoClient correoClient,
                                   AuditoriaClient auditoriaClient,
                                   PasswordEncoder passwordEncoder,
                                   Clock reloj) {
        this.usuarioRepository = usuarioRepository;
        this.politica = politica;
        this.intentosFallidos = intentosFallidos;
        this.jwtService = jwtService;
        this.correoClient = correoClient;
        this.auditoriaClient = auditoriaClient;
        this.passwordEncoder = passwordEncoder;
        this.reloj = reloj;
    }

    /**
     * @param apodo    quien cambia: el sujeto del token ya validado por el interceptor
     * @param datos    actual, nueva y confirmación
     * @param ipOrigen para el correo de aviso y la auditoría
     * @return el token nuevo de esta sesión y la confirmación
     * @throws CambioDePasswordRechazadoException con el motivo
     */
    @Transactional
    public CambioDePasswordResponse cambiar(String apodo, CambiarPasswordRequest datos, String ipOrigen) {
        Usuario usuario = usuarioRepository.findByApodo(apodo)
                .orElseThrow(() -> new IllegalStateException("No existe el usuario " + apodo));

        LocalDateTime ahora = LocalDateTime.now(reloj);
        if (usuario.getBloqueadoHasta() != null && ahora.isBefore(usuario.getBloqueadoHasta())) {
            throw new CambioDePasswordRechazadoException(Motivo.CUENTA_BLOQUEADA,
                    "Cuenta bloqueada temporalmente por intentos fallidos. Intenta de nuevo más tarde.");
        }

        if (!passwordEncoder.matches(datos.getPasswordActual(), usuario.getPassword())) {
            // Cuenta como intento fallido (RF-AUT-009); se confirma aparte
            // (REQUIRES_NEW) para que sobreviva a la excepción que sigue.
            intentosFallidos.registrarIntentoFallido(usuario.getId());
            throw new CambioDePasswordRechazadoException(Motivo.ACTUAL_INCORRECTA,
                    "La contraseña actual es incorrecta.");
        }

        if (!datos.getNuevaPassword().equals(datos.getConfirmacion())) {
            throw new CambioDePasswordRechazadoException(Motivo.CONFIRMACION,
                    "La confirmación no coincide con la contraseña nueva.");
        }

        List<String> faltan = politica.reglasQueFaltan(datos.getNuevaPassword());
        if (!faltan.isEmpty()) {
            throw new CambioDePasswordRechazadoException(Motivo.POLITICA,
                    "La contraseña nueva " + String.join("; ", faltan) + ".");
        }

        if (passwordEncoder.matches(datos.getNuevaPassword(), usuario.getPassword())) {
            throw new CambioDePasswordRechazadoException(Motivo.REPETIDA,
                    "La contraseña nueva no puede ser igual a la actual.");
        }

        usuario.setPassword(passwordEncoder.encode(datos.getNuevaPassword()));
        usuario.setVersionToken(usuario.getVersionToken() + 1);
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuarioRepository.save(usuario);

        // CA-04: esta sesión sigue válida con un token de la versión nueva.
        String tokenNuevo = jwtService.generarToken(
                usuario.getApodo(), usuario.getRol().getNombre(),
                usuario.getVersionToken(), usuario.getPublicId());

        avisarPorCorreo(usuario, ipOrigen);
        auditar(usuario, ipOrigen);

        return new CambioDePasswordResponse(tokenNuevo,
                "Contraseña actualizada. Las demás sesiones abiertas se cerraron.");
    }

    private void avisarPorCorreo(Usuario usuario, String ipOrigen) {
        correoClient.enviarCambioClave(new CorreoCambioClaveRequest(
                usuario.getEmail(), usuario.getApodo(),
                ipOrigen == null ? "desconocida" : ipOrigen,
                OffsetDateTime.now(reloj).toString()));
    }

    /**
     * HU-AUD-001: el cambio se audita, pero sin frenar al usuario si la
     * bitácora no responde (a diferencia de las acciones administrativas, donde
     * {@link AuditoriaClient} falla cerrado a propósito).
     */
    private void auditar(Usuario usuario, String ipOrigen) {
        try {
            auditoriaClient.registrar(TIPO_AUDITORIA, usuario.getApodo(), usuario.getApodo(),
                    null, null, MOTIVO_AUDITORIA, ipOrigen);
        } catch (RuntimeException bitacoraNoDisponible) {
            log.warn("El cambio de contraseña de '{}' no se pudo auditar: {}",
                    usuario.getApodo(), bitacoraNoDisponible.getMessage());
        }
    }
}
