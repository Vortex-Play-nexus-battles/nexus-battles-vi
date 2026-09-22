package com.nexusbattles.ms_identidad.notificaciones.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Prueba de integración de NotificacionClient.
 *
 * A diferencia de un test unitario, aquí Spring crea el bean con su proxy AOP,
 * así que las anotaciones @Retry y @CircuitBreaker SÍ se activan. Se apunta la
 * URL a un puerto donde no hay nada escuchando: la llamada real falla, se
 * agotan los reintentos, y se dispara el fallback fail-open. Esto ejercita el
 * método emitir() completo (construcción del request + envío + fallo + fallback),
 * y verifica el comportamiento clave: NO se propaga excepción aunque el
 * servicio de notificaciones esté caído.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.notificaciones.url=http://localhost:59999/api/v1/internal/notifications"
})
class NotificacionClientIntegrationTest {

    @Autowired
    private NotificacionClient notificacionClient;

    @Test
    void bean_seCarga() {
        assertNotNull(notificacionClient);
    }

    @Test
    void emitir_conServicioCaido_noPropaga_failOpen() {
        // Puerto 59999 no tiene nada escuchando: emitir() falla al conectar,
        // reintenta, y cae en el fallback fail-open sin propagar excepción.
        assertDoesNotThrow(() ->
            notificacionClient.emitir("42", "SANCION", "Cuenta suspendida",
                "Tu cuenta fue suspendida."));
    }

    @Test
    void emitir_variosTipos_todosFailOpen() {
        assertDoesNotThrow(() -> {
            notificacionClient.emitir("1", "CUENTA", "Perfil actualizado", "Cambios guardados.");
            notificacionClient.emitir("2", "SANCION", "Baneo", "Cuenta baneada.");
            notificacionClient.emitir("3", "OTRO", "Password", "Contraseña restablecida.");
        });
    }
}
