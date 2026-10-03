package nexus.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * El 500 generico no debe tragarse la causa. HU-PRD-014 respondio 500 en DEV
 * en cada inicio de sesion y {@code docker logs} no mostraba nada: el
 * manejador de {@code Exception} armaba el problem detail sin registrar la
 * excepcion. El cliente sigue recibiendo el mismo mensaje generico; la causa
 * va a la bitacora, con la ruta para saber que endpoint fallo.
 */
@ExtendWith(OutputCaptureExtension.class)
class ManejadorDeErroresTest {

    private static final String RUTA = "/api/v1/productos/alertas/inicio-sesion";

    @Test
    @DisplayName("un error inesperado responde 500 generico y deja la causa y la ruta en la bitacora")
    void registraLaCausaDelErrorInesperado(CapturedOutput salida) {
        MockHttpServletRequest solicitud = new MockHttpServletRequest("GET", RUTA);

        ResponseEntity<ProblemDetail> respuesta = new ManejadorDeErrores()
                .manejarErrorInesperado(
                        new IllegalStateException("consulta mal armada"),
                        solicitud);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, respuesta.getStatusCode());
        assertEquals("Error interno", respuesta.getBody().getTitle());
        assertEquals("No fue posible procesar la solicitud", respuesta.getBody().getDetail());
        assertTrue(salida.getOut().contains("GET " + RUTA), salida.getOut());
        assertTrue(salida.getOut().contains("IllegalStateException"), salida.getOut());
        assertTrue(salida.getOut().contains("consulta mal armada"), salida.getOut());
    }

    @Test
    @DisplayName("la causa interna no se filtra al cliente")
    void noFiltraLaCausaAlCliente() {
        ResponseEntity<ProblemDetail> respuesta = new ManejadorDeErrores()
                .manejarErrorInesperado(
                        new IllegalStateException("detalle interno de la base"),
                        new MockHttpServletRequest("GET", RUTA));

        assertTrue(!respuesta.getBody().getDetail().contains("detalle interno"));
        assertEquals(RUTA, respuesta.getBody().getInstance().toString());
    }
}
