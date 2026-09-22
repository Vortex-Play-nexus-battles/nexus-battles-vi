package com.nexusbattles.ms_identidad.notificaciones.client;

import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import com.nexusbattles.ms_identidad.auth.servicio.CredencialPropia;
import com.nexusbattles.ms_identidad.auth.servicio.EmisorDeTokensDeServicio;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifica que la configuración construye el RestClient del cliente de
 * notificaciones sin errores, con la credencial de servicio (ADR-005).
 */
class NotificacionClientConfigTest {

    @Test
    void crea_restClient_noNulo() {
        NotificacionClientConfig config = new NotificacionClientConfig();
        CredencialPropia credencial = new CredencialPropia(
                new EmisorDeTokensDeServicio(new ClavesDeFirma(""), "ms-identidad", 15));
        RestClient restClient = config.notificacionRestClient(credencial);
        assertNotNull(restClient);
    }
}
