package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auth.service.AuditoriaLoginClient;
import com.nexusbattles.ms_identidad.auth.servicio.CredencialPropia;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * El registro de LOGIN_FALLIDO llega a ms-cumplimiento CON la credencial de
 * servicio (R16.20). Contra un servidor HTTP de verdad en un puerto libre:
 * lo que se afirma es la cabecera que recibe cumplimiento, no una llamada
 * a un doble.
 */
@DisplayName("AuditoriaLoginClient: el login fallido se audita con la credencial de ms-identidad")
class AuditoriaLoginClientTest {

    private HttpServer cumplimiento;
    private final AtomicReference<String> autorizacionRecibida = new AtomicReference<>();
    private final AtomicReference<String> cuerpoRecibido = new AtomicReference<>();
    private volatile int respuesta = 201;

    @BeforeEach
    void levantarCumplimiento() throws IOException {
        cumplimiento = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        cumplimiento.createContext("/api/v1/admin/auditoria/eventos", intercambio -> {
            autorizacionRecibida.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            try (InputStream cuerpo = intercambio.getRequestBody()) {
                cuerpoRecibido.set(new String(cuerpo.readAllBytes(), StandardCharsets.UTF_8));
            }
            intercambio.sendResponseHeaders(respuesta, -1);
            intercambio.close();
        });
        cumplimiento.start();
    }

    @AfterEach
    void apagarCumplimiento() {
        cumplimiento.stop(0);
    }

    private String url() {
        return "http://" + cumplimiento.getAddress().getHostString() + ":" + cumplimiento.getAddress().getPort()
                + "/api/v1/admin/auditoria/eventos";
    }

    /**
     * La credencial REAL en lo que importa: su {@code intercept} es el de
     * produccion, y solo el token que firma se sustituye por uno fijo, para no
     * montar el emisor de claves en una prueba de cliente HTTP.
     */
    private static CredencialPropia credencialQuePone(String token) {
        CredencialPropia credencial = mock(CredencialPropia.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        doReturn(token).when(credencial).portador();
        return credencial;
    }

    @Test
    @DisplayName("cumplimiento recibe Authorization: Bearer con la credencial de servicio y el evento")
    void llegaConLaCredencial() {
        AuditoriaLoginClient cliente = new AuditoriaLoginClient(url(), credencialQuePone("token-de-servicio"));

        cliente.registrarLoginFallido("ana@nexus.test", "10.0.0.7");

        assertEquals("Bearer token-de-servicio", autorizacionRecibida.get());
        assertNotNull(cuerpoRecibido.get());
        assertTrue(cuerpoRecibido.get().contains("ana@nexus.test"), cuerpoRecibido.get());
        assertTrue(cuerpoRecibido.get().contains("10.0.0.7"), cuerpoRecibido.get());
    }

    @Test
    @DisplayName("si cumplimiento rechaza (401), el login sigue su curso: no se lanza nada")
    void rechazoNoRompeElLogin() {
        respuesta = 401;
        AuditoriaLoginClient cliente = new AuditoriaLoginClient(url(), credencialQuePone("token-caducado"));

        assertDoesNotThrow(() -> cliente.registrarLoginFallido("ana@nexus.test", "10.0.0.7"));
        assertEquals("Bearer token-caducado", autorizacionRecibida.get());
    }

    @Test
    @DisplayName("sin cumplimiento escuchando, tampoco se lanza nada (fail-safe)")
    void sinCumplimiento() {
        cumplimiento.stop(0);
        AuditoriaLoginClient cliente = new AuditoriaLoginClient(url(), null);

        assertDoesNotThrow(() -> cliente.registrarLoginFallido(null, null));
    }
}
