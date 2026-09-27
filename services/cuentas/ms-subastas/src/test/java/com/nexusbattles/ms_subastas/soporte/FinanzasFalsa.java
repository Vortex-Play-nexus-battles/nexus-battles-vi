package com.nexusbattles.ms_subastas.soporte;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Un ms-finanzas de mentira, por HTTP de verdad, para las pruebas de
 * integracion: {@code POST /api/v1/creditos/debitar} y {@code /reversar} con la
 * forma de {@code finanzas-creditos.yaml}. Guarda lo que recibe para que la
 * prueba compruebe que, por ejemplo, la penalizacion de cancelar salio con su
 * {@code refId} y su monto.
 *
 * <p>Los jugadores de {@link #sinSaldo} reciben el 422 {@code saldo-insuficiente}
 * del contrato; con {@link #caido} todo responde 503.
 */
public final class FinanzasFalsa implements AutoCloseable {

    /** Una operacion recibida: la ruta y su cuerpo JSON. */
    public record Operacion(String ruta, JsonNode cuerpo) {
        public String refId() {
            return cuerpo.path("refId").asText();
        }
    }

    private final HttpServer servidor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Operacion> recibidas = new CopyOnWriteArrayList<>();
    public final Set<UUID> sinSaldo = ConcurrentHashMap.newKeySet();
    public final AtomicBoolean caido = new AtomicBoolean();

    public FinanzasFalsa() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        servidor.createContext("/api/v1/creditos/", this::atender);
        servidor.start();
    }

    /** La base que se pone en {@code app.finanzas.base-url}. */
    public String base() {
        return "http://localhost:" + servidor.getAddress().getPort() + "/api/v1";
    }

    public List<Operacion> recibidas() {
        return List.copyOf(recibidas);
    }

    public List<Operacion> recibidas(String ruta) {
        return recibidas.stream().filter(o -> o.ruta().equals(ruta)).toList();
    }

    public void olvidar() {
        recibidas.clear();
        sinSaldo.clear();
        caido.set(false);
    }

    private void atender(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath().substring("/api/v1/creditos/".length());
        JsonNode cuerpo = mapper.readTree(intercambio.getRequestBody().readAllBytes());
        if (caido.get()) {
            responder(intercambio, 503, "{}");
            return;
        }
        if ("debitar".equals(ruta) && sinSaldo.contains(UUID.fromString(cuerpo.path("uid").asText()))) {
            responder(intercambio, 422, "{\"type\":\"https://nexusbattles.upb.edu.co/errors/saldo-insuficiente\","
                    + "\"title\":\"Saldo insuficiente\",\"status\":422}");
            return;
        }
        recibidas.add(new Operacion(ruta, cuerpo));
        String estado = "debitar".equals(ruta) ? "CONSUMIDA" : "LIBERADA";
        responder(intercambio, 200, mapper.createObjectNode()
                .put("refId", cuerpo.path("refId").asText())
                .put("estado", estado)
                .toString());
    }

    private static void responder(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type",
                estado >= 400 ? "application/problem+json" : "application/json");
        intercambio.sendResponseHeaders(estado, bytes.length);
        intercambio.getResponseBody().write(bytes);
        intercambio.close();
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
