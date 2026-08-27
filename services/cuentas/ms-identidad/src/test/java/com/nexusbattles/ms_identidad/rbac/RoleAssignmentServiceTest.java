package com.nexusbattles.ms_identidad.rbac;

import com.nexusbattles.ms_identidad.auth.service.AuthAdminService;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import com.nexusbattles.ms_identidad.rbac.service.RoleAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentServiceTest {

    @Mock
    private AuthAdminService authAdminService;

    private RoleAssignmentService roleAssignmentService;

    @BeforeEach
    void setUp() {
        roleAssignmentService =
                new RoleAssignmentService(authAdminService);
    }

    @Test
    void debeImpedirDegradarAlUltimoSuperAdministrador() {

        when(authAdminService.obtenerRolDeUsuario(1L))
                .thenReturn(Role.SUPER_ADMINISTRADOR);

        when(authAdminService.contarPorRol(
                Role.SUPER_ADMINISTRADOR))
                .thenReturn(1L);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> roleAssignmentService.asignarRol(
                        1L,
                        Role.ADMINISTRADOR)
        );

        assertEquals(
                "No se puede quitar el rol Super Administrador: es el único que queda en el sistema.",
                exception.getMessage()
        );

        verify(authAdminService, never())
                .actualizarRol(anyLong(), any());
    }

    @Test
    void debeCambiarRolCorrectamente() {

        when(authAdminService.obtenerRolDeUsuario(2L))
                .thenReturn(Role.JUGADOR);

        roleAssignmentService.asignarRol(
                2L,
                Role.MODERADOR
        );

        verify(authAdminService)
                .actualizarRol(2L, Role.MODERADOR);
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
                Role.ADMINISTRADOR
        );

        verify(authAdminService)
                .actualizarRol(1L, Role.ADMINISTRADOR);
    }
}