package com.nexusbattles.ms_subastas.soporte;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Un ms-finanzas de mentira, por HTTP de verdad, para las pruebas de
 * integracion: {@code POST /api/v1/creditos/debitar} y {@code /reversar} y
 * {@code GET /creditos/operaciones/{refId}} con la forma de creditos.yaml.
 * Guarda lo que recibe para que la prueba compruebe que, por ejemplo, la
 * penalizacion de cancelar salio con su {@code refId} y su monto.
 *
 * <p>Lleva un libro como el de verdad (G7): un {@code refId} ya registrado no
 * vuelve a cobrar, tampoco si se reverso, y reversar dos veces no devuelve dos
 * veces. {@link #cobradoNeto} dice cuanto le quedo cobrado a un jugador: es lo
 * que una prueba mira para saber si alguien se fue sin pagar.
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

    /** Una fila del libro falso: de quien, cuanto, por que y en que quedo. */
    private record Asiento(String uid, BigDecimal monto, String concepto, String estado) {
        Asiento devuelto() {
            return new Asiento(uid, monto, concepto, "LIBERADA");
        }
    }

    private final HttpServer servidor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Operacion> recibidas = new CopyOnWriteArrayList<>();
    private final Map<String, Asiento> libro = new ConcurrentHashMap<>();
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

    /** Las escrituras recibidas (debitar y reversar), en orden; las consultas no cuentan. */
    public List<Operacion> recibidas() {
        return List.copyOf(recibidas);
    }

    public List<Operacion> recibidas(String ruta) {
        return recibidas.stream().filter(o -> o.ruta().equals(ruta)).toList();
    }

    /** Lo que el libro tiene cobrado al jugador ahora: lo debitado y no devuelto. */
    public BigDecimal cobradoNeto(UUID uid) {
        return libro.values().stream()
                .filter(a -> a.uid().equals(uid.toString()) && "CONSUMIDA".equals(a.estado()))
                .map(Asiento::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void olvidar() {
        recibidas.clear();
        libro.clear();
        sinSaldo.clear();
        caido.set(false);
    }

    private void atender(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath().substring("/api/v1/creditos/".length());
        if (caido.get()) {
            responder(intercambio, 503, "{}");
            return;
        }
        if ("GET".equals(intercambio.getRequestMethod()) && ruta.startsWith("operaciones/")) {
            consultar(intercambio, ruta.substring("operaciones/".length()));
            return;
        }
        JsonNode cuerpo = mapper.readTree(intercambio.getRequestBody().readAllBytes());
        String refId = cuerpo.path("refId").asText();
        if ("debitar".equals(ruta) && !libro.containsKey(refId)
                && sinSaldo.contains(UUID.fromString(cuerpo.path("uid").asText()))) {
            responder(intercambio, 422, "{\"type\":\"https://nexusbattles.upb.edu.co/errors/saldo-insuficiente\","
                    + "\"title\":\"Saldo insuficiente\",\"status\":422}");
            return;
        }
        recibidas.add(new Operacion(ruta, cuerpo));
        String estado;
        if ("debitar".equals(ruta)) {
            // Idempotente por refId, como el de verdad: si ya existe, no se cobra otra vez.
            libro.putIfAbsent(refId, new Asiento(cuerpo.path("uid").asText(), cuerpo.path("monto").decimalValue(),
                    cuerpo.path("concepto").asText(), "CONSUMIDA"));
            estado = "EXITOSO";
        } else {
            Asiento asiento = libro.get(refId);
            if (asiento == null) {
                responder(intercambio, 404, "{\"type\":\"https://nexusbattles.upb.edu.co/errors/reserva-no-encontrada\","
                        + "\"title\":\"No encontrada\",\"status\":404}");
                return;
            }
            estado = "LIBERADA".equals(asiento.estado()) ? "YA_REVERSADO" : "REVERSADO";
            libro.put(refId, asiento.devuelto());
        }
        responder(intercambio, 200, mapper.createObjectNode()
                .put("refId", refId)
                .put("estado", estado)
                .toString());
    }

    private void consultar(HttpExchange intercambio, String refId) throws IOException {
        Asiento asiento = libro.get(refId);
        if (asiento == null) {
            responder(intercambio, 404, "{\"type\":\"https://nexusbattles.upb.edu.co/errors/reserva-no-encontrada\","
                    + "\"title\":\"No encontrada\",\"status\":404}");
            return;
        }
        ObjectNode operacion = mapper.createObjectNode()
                .put("refId", refId)
                .put("uid", asiento.uid())
                .put("concepto", asiento.concepto())
                .put("estado", asiento.estado());
        operacion.put("monto", asiento.monto());
        responder(intercambio, 200, operacion.toString());
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
