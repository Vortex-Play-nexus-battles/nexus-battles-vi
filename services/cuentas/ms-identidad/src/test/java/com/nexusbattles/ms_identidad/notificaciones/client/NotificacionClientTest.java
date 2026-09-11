package com.nexusbattles.ms_identidad.notificaciones.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Pruebas de NotificacionClient centradas en el comportamiento FAIL-OPEN.
 *
 * Nota técnica: las anotaciones @Retry y @CircuitBreaker de resilience4j solo
 * actúan cuando Spring envuelve el bean con su proxy AOP; en un test unitario
 * (creando el objeto con `new`) no se activan. Por eso, en vez de invocar
 * emitir() y esperar que el proxy dispare el fallback, se prueba directamente
 * el método fallback `emitirConFallback` — que es donde vive la lógica
 * fail-open real: registrar el fallo y NO propagar la excepción.
 */
class NotificacionClientTest {

    private NotificacionClient cliente;

    @BeforeEach
    void setUp() {
        RestClient restClient = RestClient.builder().build();
        cliente = new NotificacionClient(restClient);
    }

    private void invocarFallback(String usuarioId, String tipo, String titulo,
                                 String cuerpo, Throwable ex) {
        ReflectionTestUtils.invokeMethod(
            cliente, "emitirConFallback", usuarioId, tipo, titulo, cuerpo, ex);
    }

    @Test
    void fallback_noPropagaExcepcion_failOpen() {
        // El fallback recibe la excepción del servicio caído y NO debe relanzar:
        // la operación administrativa que llamó a emitir() no debe verse afectada.
        assertDoesNotThrow(() ->
            invocarFallback("42", "SANCION", "Cuenta suspendida",
                "Tu cuenta fue suspendida.",
                new RuntimeException("Connection refused")));
    }

    @Test
    void fallback_conCualquierTipo_esFailOpen() {
        assertDoesNotThrow(() ->
            invocarFallback("7", "CUENTA", "Perfil actualizado",
                "Un administrador actualizó tu perfil.",
                new RuntimeException("timeout")));
    }

    @Test
    void fallback_conCuerpoNulo_esFailOpen() {
        assertDoesNotThrow(() ->
            invocarFallback("99", "OTRO", "Titulo", null,
                new RuntimeException("circuito abierto")));
    }
}
