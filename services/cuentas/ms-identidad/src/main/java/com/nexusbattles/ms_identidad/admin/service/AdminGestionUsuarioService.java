package com.nexusbattles.ms_identidad.admin.service;

import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaClient;
import com.nexusbattles.ms_identidad.auth.service.AuthAdminService;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AdminGestionUsuarioService {

    private final AuthAdminService authAdminService;
    private final PerfilUsuarioService perfilUsuarioService;
    private final AuditoriaClient auditoriaClient;

    public AdminGestionUsuarioService(AuthAdminService authAdminService,
                                      PerfilUsuarioService perfilUsuarioService,
                                      AuditoriaClient auditoriaClient) {
        this.authAdminService = authAdminService;
        this.perfilUsuarioService = perfilUsuarioService;
        this.auditoriaClient = auditoriaClient;
    }

    public PerfilUsuario obtenerUsuarioParaGestion(Long usuarioId) {
        return perfilUsuarioService.obtenerPorUsuarioId(usuarioId);
    }

    @Transactional
    public PerfilUsuario editarPerfilDeUsuario(Long usuarioId, String nombres, String apellidos,
                                               String avatar, String biografia, String preferencias,
                                               String nuevoApodo, String administradorId, String ipOrigen) {
        PerfilUsuario actualizado = perfilUsuarioService.actualizarPerfilPropio(
            usuarioId, nombres, apellidos, avatar, biografia, preferencias, nuevoApodo
        );

        auditoriaClient.registrar(
            "ACTUALIZACION", administradorId, String.valueOf(usuarioId),
            null, "nombres=" + nombres + ", apellidos=" + apellidos,
            "Edición administrativa de perfil", ipOrigen
        );

        return actualizado;
    }

    public void suspenderCuenta(Long usuarioId, LocalDateTime suspendidoHasta, String administradorId, String ipOrigen) {
        String estadoAnterior = authAdminService.obtenerEstadoCuenta(usuarioId);
        authAdminService.actualizarEstadoCuenta(usuarioId, "SUSPENDIDA", suspendidoHasta);

        auditoriaClient.registrar(
            "SUSPENSION", administradorId, String.valueOf(usuarioId),
            estadoAnterior, "SUSPENDIDA hasta " + suspendidoHasta,
            "Suspensión de cuenta", ipOrigen
        );
    }

    public void banearCuenta(Long usuarioId, String administradorId, String ipOrigen) {
        String estadoAnterior = authAdminService.obtenerEstadoCuenta(usuarioId);
        authAdminService.actualizarEstadoCuenta(usuarioId, "BANEADA", null);

        auditoriaClient.registrar(
            "SANCION", administradorId, String.valueOf(usuarioId),
            estadoAnterior, "BANEADA",
            "Baneo definitivo de cuenta", ipOrigen
        );
    }

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
    }

    public void restablecerPassword(Long usuarioId, String administradorId, String ipOrigen) {
        authAdminService.restablecerContrasena(usuarioId);

        auditoriaClient.registrar(
            "OTRO", administradorId, String.valueOf(usuarioId),
            null, null,
            "Restablecimiento de contraseña (token de un solo uso generado)", ipOrigen
        );
    }
}
