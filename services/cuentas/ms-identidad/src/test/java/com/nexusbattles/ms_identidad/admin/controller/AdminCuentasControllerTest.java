package com.nexusbattles.ms_identidad.admin.controller;

import com.nexusbattles.ms_identidad.admin.dto.CrearCuentaAdminRequest;
import com.nexusbattles.ms_identidad.admin.dto.CuentaAdministrativaCreadaResponse;
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
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        // Antes se devolvia la entidad entera (con el resumen BCrypt de la
        // clave de relleno, la version del token y los contadores de
        // intentos). Ahora solo lo que la interfaz muestra.
        CuentaAdministrativaCreadaResponse cuerpo =
            assertInstanceOf(CuentaAdministrativaCreadaResponse.class, response.getBody());
        assertEquals("Santi", cuerpo.apodo());
        assertNull(cuerpo.uid());

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
        ProblemDetail problema = assertInstanceOf(ProblemDetail.class, response.getBody());
        assertEquals("Rol no permitido", problema.getDetail());

        verify(adminCuentaService).crearCuentaAdministrativa(
            datos,
            "admin",
            IP_ORIGEN
        );
    }
}
