package com.nexusbattles.ms_identidad.admin.controller;

import com.nexusbattles.ms_identidad.admin.dto.CrearCuentaAdminRequest;
import com.nexusbattles.ms_identidad.admin.service.AdminCuentaService;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCuentasControllerTest {

    @Mock
    private AdminCuentaService adminCuentaService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AdminCuentasController controller;

    @Test
    void debeCrearCuentaAdministrativa() {
        CrearCuentaAdminRequest datos = new CrearCuentaAdminRequest();

        Usuario usuarioCreado = new Usuario();
        usuarioCreado.setApodo("Santi");

        when(request.getHeader("X-User-Name"))
            .thenReturn("admin");

        when(adminCuentaService.crearCuentaAdministrativa(datos, "admin"))
            .thenReturn(usuarioCreado);

        ResponseEntity<?> response = controller.crearCuentaAdministrativa(
            datos,
            request
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(usuarioCreado, response.getBody());

        verify(adminCuentaService).crearCuentaAdministrativa(
            datos,
            "admin"
        );
    }

    @Test
    void debeRetornarBadRequestCuandoLosDatosSonInvalidos() {
        CrearCuentaAdminRequest datos = new CrearCuentaAdminRequest();

        when(request.getHeader("X-User-Name"))
            .thenReturn("admin");

        when(adminCuentaService.crearCuentaAdministrativa(datos, "admin"))
            .thenThrow(new IllegalArgumentException("Rol no permitido"));

        ResponseEntity<?> response = controller.crearCuentaAdministrativa(
            datos,
            request
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Rol no permitido", response.getBody());

        verify(adminCuentaService).crearCuentaAdministrativa(
            datos,
            "admin"
        );
    }
}
