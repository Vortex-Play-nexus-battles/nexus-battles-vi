package com.nexusbattles.ms_identidad.admin.controller;

import com.nexusbattles.ms_identidad.admin.dto.CrearCuentaAdminRequest;
import com.nexusbattles.ms_identidad.admin.service.AdminCuentaService;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
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

    private static final String IP_ORIGEN = "127.0.0.1";

    @Mock
    private AdminCuentaService adminCuentaService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AdminCuentasController controller;

    @BeforeEach
    void configurarRequest() {
        // La identidad ahora la deja SecurityInterceptor en el request
        // attribute "usuarioActual" (JWT/RBAC), ya no en el header X-User-Name.
        when(request.getAttribute("usuarioActual")).thenReturn("admin");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(IP_ORIGEN);
    }

    @Test
    void debeCrearCuentaAdministrativa() {
        CrearCuentaAdminRequest datos = new CrearCuentaAdminRequest();

        Usuario usuarioCreado = new Usuario();
        usuarioCreado.setApodo("Santi");

        when(adminCuentaService.crearCuentaAdministrativa(datos, "admin", IP_ORIGEN))
            .thenReturn(usuarioCreado);

        ResponseEntity<?> response = controller.crearCuentaAdministrativa(
            datos,
            request
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(usuarioCreado, response.getBody());

        verify(adminCuentaService).crearCuentaAdministrativa(
            datos,
            "admin",
            IP_ORIGEN
        );
    }

    @Test
    void debeRetornarBadRequestCuandoLosDatosSonInvalidos() {
        CrearCuentaAdminRequest datos = new CrearCuentaAdminRequest();

        when(adminCuentaService.crearCuentaAdministrativa(datos, "admin", IP_ORIGEN))
            .thenThrow(new IllegalArgumentException("Rol no permitido"));

        ResponseEntity<?> response = controller.crearCuentaAdministrativa(
            datos,
            request
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Rol no permitido", response.getBody());

        verify(adminCuentaService).crearCuentaAdministrativa(
            datos,
            "admin",
            IP_ORIGEN
        );
    }
}
