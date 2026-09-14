package com.nexusbattles.ms_subastas.subastas.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class SancionesClientHttpTest {
    private final UUID uid = UUID.randomUUID();

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void consultaHttpRealConUid(boolean activa) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/sanciones/usuarios/" + uid + "/activa", exchange -> {
            byte[] body = ("{\"sancionActiva\":" + activa + ",\"motivo\":null,\"vigenteHasta\":null}").getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var client = new SancionesClientHttp("http://localhost:" + server.getAddress().getPort(), 2000, new ObjectMapper());
            assertEquals(activa, client.tieneSancionActiva(uid));
            assertThrows(SancionesClientException.class, () -> client.tieneSancionActiva(null));
        } finally { server.stop(0); }
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 500, 204, 302})
    void rechazaStatusInesperado(int status) throws Exception {
        var http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(status);
        assertTrue(assertThrows(SancionesClientException.class, () -> cliente(http).tieneSancionActiva(uid))
                .getMessage().contains(String.valueOf(status)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "", "[]", "{mal", "{\"sancionActiva\":null}", "{\"sancionActiva\":\"false\"}", "{\"sancionActiva\":0}", "{\"sancionActiva\":false} {}"})
    void rechazaRespuestaInvalida(String body) throws Exception {
        var http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        assertThrows(SancionesClientException.class, () -> cliente(http).tieneSancionActiva(uid));
    }

    @Test
    void timeoutNoPermitePublicar() throws Exception {
        var http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException("timeout"));
        assertInstanceOf(HttpTimeoutException.class,
                assertThrows(SancionesClientException.class, () -> cliente(http).tieneSancionActiva(uid)).getCause());
    }

    @Test
    void conservaInterrupcion() throws Exception {
        var http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException());
        try {
            assertThrows(SancionesClientException.class, () -> cliente(http).tieneSancionActiva(uid));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    private SancionesClientHttp cliente(HttpClient http) {
        return new SancionesClientHttp(URI.create("http://sanciones"), http, new ObjectMapper(), Duration.ofMillis(100));
    }
}
