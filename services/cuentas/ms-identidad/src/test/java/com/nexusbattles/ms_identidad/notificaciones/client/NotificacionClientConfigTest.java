package com.nexusbattles.ms_identidad.notificaciones.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifica que la configuración construye el RestClient del cliente de
 * notificaciones sin errores.
 */
class NotificacionClientConfigTest {

    @Test
    void crea_restClient_noNulo() {
        NotificacionClientConfig config = new NotificacionClientConfig();
        RestClient restClient = config.notificacionRestClient();
        assertNotNull(restClient);
    }
}
