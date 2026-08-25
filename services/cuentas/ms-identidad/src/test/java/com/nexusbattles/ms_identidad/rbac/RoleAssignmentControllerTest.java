package com.nexusbattles.ms_identidad.rbac;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.rbac.controller.RoleAssignmentController;
import com.nexusbattles.ms_identidad.rbac.dto.ChangeRoleRequest;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import com.nexusbattles.ms_identidad.rbac.service.RoleAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoleAssignmentControllerTest {

    @Mock
    private RoleAssignmentService roleAssignmentService;

    private RoleAssignmentController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new RoleAssignmentController(roleAssignmentService);
    }

    @Test
    void debeRetornarOkCuandoCambioDeRolEsExitoso() {

        ChangeRoleRequest request = new ChangeRoleRequest();
        request.setIdSolicitante(1L);
        request.setNuevoRol(Role.MODERADOR);

        Usuario usuario = new Usuario();
        usuario.setId(2L);
        usuario.setRol(Role.MODERADOR.getDisplayName());

        when(roleAssignmentService.cambiarRol(
                1L,
                2L,
                Role.MODERADOR))
                .thenReturn(usuario);

        ResponseEntity<?> response =
                controller.cambiarRol(2L, request);

        assertEquals(200, response.getStatusCode().value());

        verify(roleAssignmentService)
                .cambiarRol(1L, 2L, Role.MODERADOR);
    }

    @Test
    void debeRetornarBadRequestCuandoCambioEsRechazado() {

        ChangeRoleRequest request = new ChangeRoleRequest();
        request.setIdSolicitante(3L);
        request.setNuevoRol(Role.MODERADOR);

        when(roleAssignmentService.cambiarRol(
                3L,
                2L,
                Role.MODERADOR))
                .thenThrow(new RuntimeException(
                        "Solo un Super Administrador puede modificar roles."));

        ResponseEntity<?> response =
                controller.cambiarRol(2L, request);

        assertEquals(400, response.getStatusCode().value());

        verify(roleAssignmentService)
                .cambiarRol(3L, 2L, Role.MODERADOR);
    }
}