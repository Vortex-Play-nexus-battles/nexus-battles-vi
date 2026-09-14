package com.nexusbattles.ms_subastas.subastas.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CatalogoProductosClientHttpTest {
    private final HttpClient http = mock(HttpClient.class);
    private final HttpResponse<String> response = mock(HttpResponse.class);
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private CatalogoProductosClientHttp client;
    private UUID id;

    @BeforeEach
    void setUp() { id = UUID.randomUUID(); client = new CatalogoProductosClientHttp(URI.create("http://catalogo:8080"), http, mapper, Duration.ofSeconds(1)); }

    @Test
    void mapeaLosCamposRealesYDejaNulosLosQueNoExisten() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"id\":\"" + id + "\",\"nombre\":\"Espada\",\"imagen\":\"img.png\",\"descripcion\":\"Desc\",\"tipo\":\"ARMA\",\"premium\":false,\"estado\":\"ACTIVO\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var producto = client.buscar(id).orElseThrow();
        assertEquals("Espada", producto.nombre());
        assertEquals("img.png", producto.miniaturaUrl());
        assertEquals("Desc", producto.descripcionCorta());
        assertNull(producto.rareza());
        assertNull(producto.habilidades());
        assertTrue(producto.subastable());
    }

    @Test
    void un404SeRepresentaComoAusencia() throws Exception {
        when(response.statusCode()).thenReturn(404);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        assertEquals(Optional.empty(), client.buscar(id));
    }

    @Test
    void jsonInvalidoSeRechazaComoRespuestaInvalida() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{malformed");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        assertThrows(CatalogoProductosClientException.class, () -> client.buscar(id));
    }

    @Test
    void indisponibilidadSeExponeComoErrorDelAdaptador() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new java.io.IOException("down"));
        assertThrows(CatalogoProductosClientException.class, () -> client.buscar(id));
    }
}
