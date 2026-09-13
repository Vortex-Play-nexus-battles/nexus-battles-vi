package com.nexusbattles.ms_subastas.subastas.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventarioClientHttpTest {

    private final HttpClient http = mock(HttpClient.class);
    @SuppressWarnings("unchecked")
    private final HttpResponse<String> response = mock(HttpResponse.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private InventarioClientHttp client;

    @BeforeEach
    void setUp() {
        client = new InventarioClientHttp(URI.create("http://inventario:8080"), http, mapper, Duration.ofSeconds(1));
    }

    // --- buscar ---

    @Test
    void buscarMapeaCamposCorrectamenteEn200() throws Exception {
        UUID prodId = UUID.randomUUID();
        UUID propId = UUID.randomUUID();
        String json = """
                {
                    "id": "elem-123",
                    "productoId": "%s",
                    "propietarioId": "%s",
                    "enUso": true
                }
                """.formatted(prodId, propId);

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(json);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        Optional<InventarioClient.ElementoInventario> elemento = client.buscar("elem-123");
        assertTrue(elemento.isPresent());
        assertEquals("elem-123", elemento.get().id());
        assertEquals(prodId, elemento.get().productoId());
        assertEquals(propId, elemento.get().propietarioId());
        assertTrue(elemento.get().enUso());
    }

    @Test
    void buscarDevuelveVacioEn404() throws Exception {
        when(response.statusCode()).thenReturn(404);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        Optional<InventarioClient.ElementoInventario> elemento = client.buscar("elem-inexistente");
        assertTrue(elemento.isEmpty());
    }

    @Test
    void buscarLanzaExcepcionEn500() throws Exception {
        when(response.statusCode()).thenReturn(500);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThrows(InventarioClientException.class, () -> client.buscar("elem-err"));
    }

    @Test
    void buscarLanzaExcepcionConJsonInvalido() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{invalido");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThrows(InventarioClientException.class, () -> client.buscar("elem-malformado"));
    }

    @Test
    void buscarLanzaExcepcionSiIdEsNuloOBlanco() {
        assertThrows(InventarioClientException.class, () -> client.buscar(null));
        assertThrows(InventarioClientException.class, () -> client.buscar("  "));
    }

    // --- reservar ---

    @Test
    void reservarEnviaPeticionConExito() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertDoesNotThrow(() ->
                client.reservar("elem-1", UUID.randomUUID(), UUID.randomUUID(), "idem-res"));
    }

    @Test
    void reservarLanzaExcepcionEnFalloHttp() throws Exception {
        when(response.statusCode()).thenReturn(400);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThrows(InventarioClientException.class, () ->
                client.reservar("elem-1", UUID.randomUUID(), UUID.randomUUID(), "idem-res"));
    }

    @Test
    void reservarLanzaExcepcionSiIdEsNulo() {
        assertThrows(InventarioClientException.class, () ->
                client.reservar(null, UUID.randomUUID(), UUID.randomUUID(), "idem-res"));
    }

    // --- liberarReserva ---

    @Test
    void liberarReservaEnviaDeleteConExito() throws Exception {
        when(response.statusCode()).thenReturn(204);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertDoesNotThrow(() ->
                client.liberarReserva("elem-1", UUID.randomUUID(), "idem-lib"));
    }

    @Test
    void liberarReservaTolera404ComoExitoIdempotente() throws Exception {
        when(response.statusCode()).thenReturn(404);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertDoesNotThrow(() ->
                client.liberarReserva("elem-1", UUID.randomUUID(), "idem-lib"));
    }

    @Test
    void liberarReservaLanzaExcepcionEn500() throws Exception {
        when(response.statusCode()).thenReturn(500);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThrows(InventarioClientException.class, () ->
                client.liberarReserva("elem-1", UUID.randomUUID(), "idem-lib"));
    }

    @Test
    void liberarReservaLanzaExcepcionSiIdEsNulo() {
        assertThrows(InventarioClientException.class, () ->
                client.liberarReserva(null, UUID.randomUUID(), "idem-lib"));
    }

    // --- transferirProducto ---

    @Test
    void transferirProductoEnviaPeticionConExito() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertDoesNotThrow(() ->
                client.transferirProducto("elem-1", UUID.randomUUID(), UUID.randomUUID(), "idem-tra"));
    }

    @Test
    void transferirProductoLanzaExcepcionEnFalloHttp() throws Exception {
        when(response.statusCode()).thenReturn(500);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThrows(InventarioClientException.class, () ->
                client.transferirProducto("elem-1", UUID.randomUUID(), UUID.randomUUID(), "idem-tra"));
    }

    @Test
    void transferirProductoValidaParametrosObligatorios() {
        assertThrows(InventarioClientException.class, () ->
                client.transferirProducto(null, UUID.randomUUID(), UUID.randomUUID(), "idem"));
        assertThrows(InventarioClientException.class, () ->
                client.transferirProducto("elem-1", null, UUID.randomUUID(), "idem"));
    }

    // --- red e interrupciones ---

    @Test
    void errorDeRedSeTraduceEnInventarioClientException() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Timeout de conexion"));

        assertThrows(InventarioClientException.class, () -> client.buscar("elem-1"));
    }

    @Test
    void hiloInterrumpidoRestauraEstadoYTraduceExcepcion() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("interrupted"));

        assertThrows(InventarioClientException.class, () -> client.buscar("elem-1"));
        assertTrue(Thread.currentThread().isInterrupted());
        // Limpiamos el flag de interrupcion para no afectar otros tests
        Thread.interrupted();
    }
}
