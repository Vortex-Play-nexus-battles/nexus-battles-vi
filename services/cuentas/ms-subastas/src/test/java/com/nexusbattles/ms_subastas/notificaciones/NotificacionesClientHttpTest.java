package com.nexusbattles.ms_subastas.notificaciones;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Se prueba contra un servidor HTTP de verdad (el del JDK, sin dependencias
 * nuevas) en vez de un mock del HttpClient: lo que hay que verificar es el JSON
 * que sale por el cable y como se interpreta cada codigo, y un mock del cliente
 * no comprueba ninguna de las dos cosas.
 */
class NotificacionesClientHttpTest {

    private HttpServer servidor;
    private NotificacionesClientHttp cliente;
    private final AtomicReference<String> ultimoCuerpo = new AtomicReference<>();
    private final AtomicInteger codigoARespondar = new AtomicInteger(201);
    private final AtomicInteger llamadas = new AtomicInteger(0);

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/api/v1/internal/notifications", intercambio -> {
            llamadas.incrementAndGet();
            ultimoCuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            intercambio.sendResponseHeaders(codigoARespondar.get(), -1);
            intercambio.close();
        });
        servidor.start();

        cliente = new NotificacionesClientHttp(
                URI.create("http://localhost:" + servidor.getAddress().getPort() + "/api/v1"),
                HttpClient.newHttpClient(), new ObjectMapper(), Duration.ofSeconds(2));
    }

    @AfterEach
    void bajarServidor() {
        servidor.stop(0);
    }

    private NotificacionesClient.Aviso avisoDePrueba() {
        return new NotificacionesClient.Aviso(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                UUID.fromString("66666666-7777-8888-9999-000000000000"),
                TipoNotificacion.SUBASTA_CERRADA_POR_COMPRA_INMEDIATA,
                "La subasta se cerro",
                "Otro jugador la compro de forma inmediata.",
                Instant.parse("2026-09-13T18:00:00Z"));
    }

    @Test
    void enviaExactamenteLosCamposQueExigeElContrato() throws Exception {
        cliente.entregar(avisoDePrueba());

        JsonNode json = new ObjectMapper().readTree(ultimoCuerpo.get());
        assertEquals("66666666-7777-8888-9999-000000000000", json.get("usuarioId").asText());
        assertEquals("11111111-2222-3333-4444-555555555555", json.get("id").asText());
        assertEquals("SUBASTA_CERRADA_POR_COMPRA_INMEDIATA", json.get("tipo").asText());
        assertEquals("La subasta se cerro", json.get("titulo").asText());
        assertEquals("Otro jugador la compro de forma inmediata.", json.get("cuerpo").asText());

        // date-time por contrato. El ObjectMapper de este servicio no desactiva
        // WRITE_DATES_AS_TIMESTAMPS, asi que sin formateo explicito aqui saldria
        // un numero epoch y el otro modulo lo rechazaria con un 400.
        assertEquals("2026-09-13T18:00:00Z", json.get("creadaEn").asText());
    }

    /**
     * 409 es "ya existe un aviso con ese identificador": el intento anterior si
     * llego y lo que se perdio fue la respuesta. Si esto lanzara, la fila se
     * quedaria sin marcar y el drenador la reintentaria para siempre.
     */
    @Test
    void un409SeTomaComoEntregadoPorqueElAvisoYaEstaEnLaBandeja() {
        codigoARespondar.set(409);

        assertDoesNotThrow(() -> cliente.entregar(avisoDePrueba()));
    }

    @Test
    void un400SeReportaComoFalloYNoSeDaPorEntregado() {
        codigoARespondar.set(400);

        assertThrows(NotificacionesClientException.class, () -> cliente.entregar(avisoDePrueba()));
    }

    @Test
    void siElModuloNoRespondeSeReportaComoFallo() {
        NotificacionesClientHttp haciaLaNada = new NotificacionesClientHttp(
                URI.create("http://localhost:1/api/v1"),
                HttpClient.newHttpClient(), new ObjectMapper(), Duration.ofMillis(300));

        assertThrows(NotificacionesClientException.class, () -> haciaLaNada.entregar(avisoDePrueba()));
    }

    @Test
    void un201SeEntregaUnaSolaVez() {
        cliente.entregar(avisoDePrueba());

        assertEquals(1, llamadas.get());
    }
}
