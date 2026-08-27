package com.nexusbattles.ms_identidad.rbac.service;

import com.nexusbattles.ms_identidad.auth.service.AuthAdminService;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import org.springframework.stereotype.Service;

@Service
public class RoleAssignmentService {

    private final AuthAdminService authAdminService;

    public RoleAssignmentService(AuthAdminService authAdminService) {
        this.authAdminService = authAdminService;
    }

    public void asignarRol(Long usuarioId, Role nuevoRol) {

        Role rolActual = authAdminService.obtenerRolDeUsuario(usuarioId);

        // Regla: no dejar el sistema sin ningún Super Administrador.
        if (rolActual == Role.SUPER_ADMINISTRADOR
                && nuevoRol != Role.SUPER_ADMINISTRADOR) {

            long totalSuperAdministradores =
                    authAdminService.contarPorRol(Role.SUPER_ADMINISTRADOR);

            if (totalSuperAdministradores <= 1) {
                throw new IllegalStateException(
                        "No se puede quitar el rol Super Administrador: es el único que queda en el sistema.");
            }
        }

        authAdminService.actualizarRol(usuarioId, nuevoRol);

        // TODO [INTEGRACIÓN FUTURA - HU-AUT-004]:
        // Invalidar las sesiones activas de la cuenta afectada.

        // TODO [INTEGRACIÓN FUTURA - HU-AUD-001]:
        // Registrar el cambio de rol con valor anterior y nuevo.
    }
}
