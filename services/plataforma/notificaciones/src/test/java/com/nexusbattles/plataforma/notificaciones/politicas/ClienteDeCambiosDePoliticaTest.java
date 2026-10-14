package com.nexusbattles.plataforma.notificaciones.politicas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;
import com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza;
import com.sun.net.httpserver.HttpServer;

/**
 * SA-11 — el cliente de {@code GET /parametros/politicas/cambios/posteriores}
 * (admin-parametros.yaml 1.1.0) tal como lo arma
 * {@link ConfiguracionDePoliticas}, contra un servidor HTTP de verdad.
 */
@DisplayName("Notificaciones · cliente de los cambios en las politicas de uso")
class ClienteDeCambiosDePoliticaTest {

    private HttpServer servidor;
    private final List<String> rutas = new CopyOnWriteArrayList<>();
    private final List<String> consultas = new CopyOnWriteArrayList<>();
    private final List<String> autorizaciones = new CopyOnWriteArrayList<>();
    private final List<String> trazas = new CopyOnWriteArrayList<>();
    private volatile int estado = 200;
    private volatile String cuerpo = "{}";
    private volatile long esperaMs;

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            rutas.add(intercambio.getRequestURI().getPath());
            consultas.add(String.valueOf(intercambio.getRequestURI().getQuery()));
            autorizaciones.add(String.valueOf(intercambio.getRequestHeaders().getFirst("Authorization")));
            trazas.add(String.valueOf(intercambio.getRequestHeaders().getFirst(FiltroDeTraza.CABECERA)));
            try {
                Thread.sleep(esperaMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] respuesta = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, respuesta.length);
            intercambio.getResponseBody().write(respuesta);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
        MDC.clear();
    }

    private ClienteDeCambiosDePolitica cliente(int lecturaMs) {
        return ConfiguracionDePoliticas.cliente(
                "http://127.0.0.1:" + servidor.getAddress().getPort() + "/api/v1/",
                500, lecturaMs, 50, new InterceptorDeTraza());
    }

    @Test
    @DisplayName("pide lo posterior a desde por la ruta del contrato, sin credencial, y lee cada cambio")
    void lote() {
        cuerpo = """
                {"hasta":"2026-10-14T14:30:00.123456Z","completo":false,"cambios":[
                  {"id":"9d0c6a52-7a59-4d4b-8f3e-2d64c1b2a001","politica":"POLITICA_DE_PRIVACIDAD",
                   "nombreDeLaPolitica":"Política de privacidad","titulo":"Datos del chat",
                   "descripcion":"Los mensajes del chat se guardan noventa días.",
                   "vigenteDesde":"2026-10-20T05:00:00Z","publicadoEn":"2026-10-14T14:30:00.123456Z",
                   "campoNuevo":"se ignora"}]}
                """;

        LoteDeCambiosDePolitica lote = cliente(1_000).consultar(Instant.parse("2026-09-14T15:00:00Z"));

        assertEquals(List.of("/api/v1/parametros/politicas/cambios/posteriores"), rutas);
        assertEquals(List.of("desde=2026-09-14T15:00:00Z&limite=50"), consultas);
        assertEquals(List.of("null"), autorizaciones, "la lectura es publica: no viaja ninguna credencial");
        assertFalse(lote.completo());
        assertEquals(Instant.parse("2026-10-14T14:30:00.123456Z"), lote.hasta());
        CambioDePoliticaDeUso cambio = lote.cambios().getFirst();
        assertEquals("POLITICA_DE_PRIVACIDAD", cambio.politica());
        assertEquals("Datos del chat", cambio.titulo());
        assertEquals(Instant.parse("2026-10-20T05:00:00Z"), cambio.vigenteDesde());
    }

    @Test
    @DisplayName("sin el campo completo se da por completo; la traza viaja en traceparent (regla 5)")
    void completoYTraza() {
        cuerpo = "{\"hasta\":\"2026-10-14T15:00:00Z\",\"cambios\":[]}";
        MDC.put(FiltroDeTraza.CLAVE_MDC, "4bf92f3577b34da6a3ce929d0e0e4736");

        assertTrue(cliente(1_000).consultar(Instant.parse("2026-10-14T15:00:00Z")).completo());
        assertTrue(trazas.getFirst().startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-"), trazas.getFirst());
    }

    @Test
    @DisplayName("un 503 o un 400, o una respuesta que no es un lote, es «no disponible»")
    void errores() {
        Instant desde = Instant.parse("2026-10-14T15:00:00Z");
        estado = 503;
        cuerpo = "{\"status\":503}";
        assertThrows(PoliticasNoDisponibles.class, () -> cliente(1_000).consultar(desde));
        estado = 400;
        assertThrows(PoliticasNoDisponibles.class, () -> cliente(1_000).consultar(desde));

        estado = 200;
        cuerpo = "{\"completo\":true,\"cambios\":[]}";
        assertThrows(PoliticasNoDisponibles.class, () -> cliente(1_000).consultar(desde));
        cuerpo = "{\"hasta\":\"2026-10-14T15:00:00Z\",\"completo\":true}";
        assertThrows(PoliticasNoDisponibles.class, () -> cliente(1_000).consultar(desde));
        cuerpo = "<html>borde</html>";
        assertThrows(PoliticasNoDisponibles.class, () -> cliente(1_000).consultar(desde));
    }

    @Test
    @DisplayName("un admin-parametros colgado se corta en el tiempo de lectura y no se reintenta")
    void tiempoDeEspera() {
        esperaMs = 2_000;
        cuerpo = "{\"hasta\":\"2026-10-14T15:00:00Z\",\"completo\":true,\"cambios\":[]}";

        Instant inicio = Instant.now();
        assertThrows(PoliticasNoDisponibles.class,
                () -> cliente(300).consultar(Instant.parse("2026-10-14T15:00:00Z")));

        assertTrue(Duration.between(inicio, Instant.now()).compareTo(Duration.ofMillis(1_500)) < 0);
        assertEquals(1, rutas.size(), "sin reintentos: una sola peticion");
    }
}
