package com.nexusbattles.ms_identidad.admin.service;

import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaClient;
import com.nexusbattles.ms_identidad.auth.service.AuthAdminService;
import com.nexusbattles.ms_identidad.auth.service.AvatarStorageService;
import com.nexusbattles.ms_identidad.notificaciones.client.NotificacionClient;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
public class AdminGestionUsuarioService {

    private final AuthAdminService authAdminService;
    private final PerfilUsuarioService perfilUsuarioService;
    private final AuditoriaClient auditoriaClient;
    private final AvatarStorageService avatarStorageService;
    private final NotificacionClient notificacionClient;

    public AdminGestionUsuarioService(AuthAdminService authAdminService,
                                      PerfilUsuarioService perfilUsuarioService,
                                      AuditoriaClient auditoriaClient,
                                      AvatarStorageService avatarStorageService,
                                      NotificacionClient notificacionClient) {
        this.authAdminService = authAdminService;
        this.perfilUsuarioService = perfilUsuarioService;
        this.auditoriaClient = auditoriaClient;
        this.avatarStorageService = avatarStorageService;
        this.notificacionClient = notificacionClient;
    }

    public PerfilUsuario obtenerUsuarioParaGestion(Long usuarioId) {
        return perfilUsuarioService.obtenerPorUsuarioId(usuarioId);
    }

    @Transactional
    public PerfilUsuario editarPerfilDeUsuario(Long usuarioId, String nombres, String apellidos,
                                               MultipartFile nuevoAvatar, String preferencias,
                                               String nuevoApodo, String administradorId, String ipOrigen) {
        PerfilUsuario actualizado = perfilUsuarioService.actualizarPerfilPropio(
            usuarioId, nombres, apellidos, nuevoAvatar, preferencias, nuevoApodo
        );

        auditoriaClient.registrar(
            "ACTUALIZACION", administradorId, String.valueOf(usuarioId),
            null, "nombres=" + nombres + ", apellidos=" + apellidos,
            "Edición administrativa de perfil", ipOrigen
        );

        // Aviso al usuario afectado (HU-USR-003). Fail-open: si notificaciones
        // no responde, la operación ya está hecha y no se revierte.
        notificacionClient.emitir(
            String.valueOf(usuarioId),
            "CUENTA",
            "Tu perfil fue actualizado",
            "Un administrador actualizó la información de tu perfil."
        );

        return actualizado;
    }

    // @Transactional obligatorio (HU-AUD-001, fail-closed): el cambio de estado
    // y el registro de auditoría deben ir en la MISMA transacción. Si la
    // auditoría falla, AuditoriaClient lanza excepción y Spring revierte el
    // cambio de estado ya hecho, para que la operación no se consuma sin auditoría.
    // La notificación va DESPUÉS de la auditoría y es fail-open (no revierte).
    @Transactional
    public void suspenderCuenta(Long usuarioId, LocalDateTime suspendidoHasta, String administradorId, String ipOrigen) {
        String estadoAnterior = authAdminService.obtenerEstadoCuenta(usuarioId);
        authAdminService.actualizarEstadoCuenta(usuarioId, "SUSPENDIDA", suspendidoHasta);

        auditoriaClient.registrar(
            "SUSPENSION", administradorId, String.valueOf(usuarioId),
            estadoAnterior, "SUSPENDIDA hasta " + suspendidoHasta,
            "Suspensión de cuenta", ipOrigen
        );

        notificacionClient.emitir(
            String.valueOf(usuarioId),
            "SANCION",
            "Tu cuenta fue suspendida",
            "Tu cuenta ha sido suspendida hasta " + suspendidoHasta + "."
        );
    }

    @Transactional
    public void banearCuenta(Long usuarioId, String administradorId, String ipOrigen) {
        String estadoAnterior = authAdminService.obtenerEstadoCuenta(usuarioId);
        authAdminService.actualizarEstadoCuenta(usuarioId, "BANEADA", null);

        auditoriaClient.registrar(
            "SANCION", administradorId, String.valueOf(usuarioId),
            estadoAnterior, "BANEADA",
            "Baneo definitivo de cuenta", ipOrigen
        );

        notificacionClient.emitir(
            String.valueOf(usuarioId),
            "SANCION",
            "Tu cuenta fue baneada",
            "Tu cuenta ha sido baneada de forma definitiva."
        );
    }

    @Transactional
    public void reactivarCuenta(Long usuarioId, String administradorId, String ipOrigen) {
        String estadoAnterior = authAdminService.obtenerEstadoCuenta(usuarioId);
        if ("BANEADA".equals(estadoAnterior)) {
            throw new IllegalArgumentException(
                "No se puede reactivar una cuenta baneada definitivamente.");
        }

        authAdminService.actualizarEstadoCuenta(usuarioId, "ACTIVO", null);

        auditoriaClient.registrar(
            "ACTUALIZACION", administradorId, String.valueOf(usuarioId),
            estadoAnterior, "ACTIVO",
            "Reactivación de cuenta", ipOrigen
        );

        notificacionClient.emitir(
            String.valueOf(usuarioId),
            "CUENTA",
            "Tu cuenta fue reactivada",
            "Tu cuenta ha sido reactivada y ya puedes volver a acceder."
        );
    }

    @Transactional
    public void restablecerPassword(Long usuarioId, String administradorId, String ipOrigen) {
        authAdminService.restablecerContrasena(usuarioId);

        auditoriaClient.registrar(
            "OTRO", administradorId, String.valueOf(usuarioId),
            null, null,
            "Restablecimiento de contraseña (token de un solo uso generado)", ipOrigen
        );

        notificacionClient.emitir(
            String.valueOf(usuarioId),
            "CUENTA",
            "Se restableció tu contraseña",
            "Un administrador restableció tu contraseña. Revisa tu correo para establecer una nueva."
        );
    }
}
