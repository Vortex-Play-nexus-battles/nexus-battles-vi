package com.nexusbattles.ms_identidad.rbac;

import com.nexusbattles.ms_identidad.auth.service.AuthAdminService;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import com.nexusbattles.ms_identidad.rbac.service.AuditoriaCambioRolClient;
import com.nexusbattles.ms_identidad.rbac.service.RoleAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentServiceTest {

    @Mock
    private AuthAdminService authAdminService;

    @Mock
    private AuditoriaCambioRolClient auditoriaCambioRolClient;

    private RoleAssignmentService roleAssignmentService;

    @BeforeEach
    void setUp() {
        roleAssignmentService =
            new RoleAssignmentService(
                authAdminService,
                auditoriaCambioRolClient
            );
    }

    @Test
    void debeImpedirDegradarAlUltimoSuperAdministrador() {

        when(authAdminService.obtenerRolDeUsuario(1L))
            .thenReturn(Role.SUPER_ADMINISTRADOR);

        when(authAdminService.contarPorRol(
            Role.SUPER_ADMINISTRADOR))
            .thenReturn(1L);

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () -> roleAssignmentService.asignarRol(
                    1L,
                    Role.ADMINISTRADOR,
                    "super_admin",
                    "10.0.0.10"
                )
            );

        assertEquals(
            "No se puede quitar el rol Super Administrador: es el único que queda en el sistema.",
            exception.getMessage()
        );

        verifyNoInteractions(
            auditoriaCambioRolClient
        );

        verify(authAdminService, never())
            .actualizarRol(
                anyLong(),
                any()
            );
    }

    @Test
    void debeCambiarRolCorrectamenteYRegistrarAuditoria() {

        when(authAdminService.obtenerRolDeUsuario(2L))
            .thenReturn(Role.JUGADOR);

        roleAssignmentService.asignarRol(
            2L,
            Role.MODERADOR,
            "super_admin",
            "10.0.0.20"
        );

        verify(auditoriaCambioRolClient)
            .registrarCambioRol(
                "super_admin",
                2L,
                Role.JUGADOR,
                Role.MODERADOR,
                "10.0.0.20"
            );

        verify(authAdminService)
            .actualizarRol(
                2L,
                Role.MODERADOR
            );

        InOrder orden =
            inOrder(
                auditoriaCambioRolClient,
                authAdminService
            );

        orden.verify(auditoriaCambioRolClient)
            .registrarCambioRol(
                "super_admin",
                2L,
                Role.JUGADOR,
                Role.MODERADOR,
                "10.0.0.20"
            );

        orden.verify(authAdminService)
            .actualizarRol(
                2L,
                Role.MODERADOR
            );
    }

    @Test
    void debePermitirCambiarRolDeSuperAdministradorSiExisteOtro() {

        when(authAdminService.obtenerRolDeUsuario(1L))
            .thenReturn(Role.SUPER_ADMINISTRADOR);

        when(authAdminService.contarPorRol(
            Role.SUPER_ADMINISTRADOR))
            .thenReturn(2L);

        roleAssignmentService.asignarRol(
            1L,
            Role.ADMINISTRADOR,
            "super_admin_principal",
            "10.0.0.30"
        );

        verify(auditoriaCambioRolClient)
            .registrarCambioRol(
                "super_admin_principal",
                1L,
                Role.SUPER_ADMINISTRADOR,
                Role.ADMINISTRADOR,
                "10.0.0.30"
            );

        verify(authAdminService)
            .actualizarRol(
                1L,
                Role.ADMINISTRADOR
            );
    }

    @Test
    void noDebeCambiarRolSiLaAuditoriaFalla() {

        when(authAdminService.obtenerRolDeUsuario(2L))
            .thenReturn(Role.JUGADOR);

        doThrow(
            new IllegalStateException(
                "ms-cumplimiento no disponible"
            )
        )
            .when(auditoriaCambioRolClient)
            .registrarCambioRol(
                "super_admin",
                2L,
                Role.JUGADOR,
                Role.MODERADOR,
                "10.0.0.40"
            );

        assertThrows(
            IllegalStateException.class,
            () -> roleAssignmentService.asignarRol(
                2L,
                Role.MODERADOR,
                "super_admin",
                "10.0.0.40"
            )
        );

        verify(authAdminService, never())
            .actualizarRol(
                anyLong(),
                any()
            );
    }
}
