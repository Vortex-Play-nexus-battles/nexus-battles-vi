package com.nexusbattles.ms_identidad.rbac.service;

import com.nexusbattles.ms_identidad.auth.service.AuthAdminService;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleAssignmentService {

    private final AuthAdminService authAdminService;
    private final AuditoriaCambioRolClient auditoriaCambioRolClient;

    public RoleAssignmentService(
        AuthAdminService authAdminService,
        AuditoriaCambioRolClient auditoriaCambioRolClient) {

        this.authAdminService = authAdminService;
        this.auditoriaCambioRolClient = auditoriaCambioRolClient;
    }

    @Transactional
    public void asignarRol(
        Long usuarioId,
        Role nuevoRol,
        String administradorId,
        String ipOrigen) {

        Role rolActual =
            authAdminService.obtenerRolDeUsuario(usuarioId);

        // HU-RBAC-003:
        // no dejar el sistema sin ningún Super Administrador.
        if (rolActual == Role.SUPER_ADMINISTRADOR
            && nuevoRol != Role.SUPER_ADMINISTRADOR) {

            long totalSuperAdministradores =
                authAdminService.contarPorRol(
                    Role.SUPER_ADMINISTRADOR);

            if (totalSuperAdministradores <= 1) {
                throw new IllegalStateException(
                    "No se puede quitar el rol Super Administrador: es el único que queda en el sistema."
                );
            }
        }

        /*
         * Auditoría fail-closed.
         *
         * Primero se registra CAMBIO_ROL.
         * Solamente si ms-cumplimiento responde correctamente
         * se procede con la modificación del usuario.
         */
        auditoriaCambioRolClient.registrarCambioRol(
            administradorId,
            usuarioId,
            rolActual,
            nuevoRol,
            ipOrigen
        );

        authAdminService.actualizarRol(
            usuarioId,
            nuevoRol
        );

        /*
         * Pendiente HU-RBAC-003:
         * invalidación de JWT activos después del cambio de rol.
         */
    }
}
