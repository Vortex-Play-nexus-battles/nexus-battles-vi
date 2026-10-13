package com.nexusbattles.ms_identidad.admin.controller;

import com.nexusbattles.ms_identidad.admin.ficha.FichaAdministrativa;
import com.nexusbattles.ms_identidad.admin.ficha.FichaAdministrativaService;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import com.nexusbattles.ms_identidad.sanciones.CuentaNoEncontradaException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

/** HU-USR-010 — la ruta de la ficha administrativa (ms-identidad-admin.yaml 1.4.0). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminFichaControllerTest {

    private static final FichaAdministrativa FICHA = new FichaAdministrativa(15L,
            UUID.fromString("11111111-2222-3333-4444-555555555555"), "Ana", "ana@nexus.test", "Ana María", "Rueda",
            null, "JUGADOR", "ACTIVO", null, false, LocalDateTime.of(2026, 9, 1, 10, 30), null, true);

    @Mock
    private FichaAdministrativaService fichas;

    @Mock
    private HttpServletRequest request;

    private AdminFichaController controller;

    @BeforeEach
    void preparar() {
        when(request.getAttribute("usuarioActual")).thenReturn("simon_superadmin");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getRequestURI()).thenReturn("/api/v1/admin/usuarios/99/ficha");
        controller = new AdminFichaController(fichas);
    }

    @Test
    void entregaLaFichaDeQuienPideYSinGuardarlaEnNingunaCache() {
        when(fichas.consultar("15", "simon_superadmin", "127.0.0.1")).thenReturn(FICHA);

        ResponseEntity<FichaAdministrativa> respuesta = controller.consultar("15", request);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertSame(FICHA, respuesta.getBody());
        assertEquals("no-store", respuesta.getHeaders().getCacheControl());
    }

    @Test
    void laIpDeLaAuditoriaEsLaPrimeraDeXForwardedFor() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");
        when(fichas.consultar("15", "simon_superadmin", "203.0.113.5")).thenReturn(FICHA);

        assertSame(FICHA, controller.consultar("15", request).getBody());
    }

    @Test
    void unaCuentaQueNoExisteEsUnProblemDetailsCon404() {
        ResponseEntity<ProblemDetail> respuesta = controller.noEncontrada(new CuentaNoEncontradaException(), request);

        assertEquals(HttpStatus.NOT_FOUND, respuesta.getStatusCode());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, respuesta.getHeaders().getContentType());
        ProblemDetail problema = respuesta.getBody();
        assertEquals("https://nexusbattles.upb.edu.co/errors/cuenta-no-encontrada", problema.getType().toString());
        assertEquals("Cuenta no encontrada", problema.getTitle());
        assertEquals("/api/v1/admin/usuarios/99/ficha", problema.getInstance().toString());
    }

    @Test
    void laRutaEsLaDelContratoYPideElPermisoDeGestionarCuentas() throws NoSuchMethodException {
        Method metodo = AdminFichaController.class.getMethod("consultar", String.class, HttpServletRequest.class);

        assertArrayEquals(new String[] {"/api/v1/admin/usuarios"},
                AdminFichaController.class.getAnnotation(RequestMapping.class).value());
        assertArrayEquals(new String[] {"/{usuario}/ficha"}, metodo.getAnnotation(GetMapping.class).value());
        assertEquals(Action.GESTIONAR_CUENTAS, metodo.getAnnotation(RequirePermission.class).value());
    }
}
