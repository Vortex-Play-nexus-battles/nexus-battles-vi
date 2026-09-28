package com.nexusbattles.ms_ecommerce.compra;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Los seis servicios con los que habla la compra, en un servidor HTTP del JDK
 * que contesta lo que contestaria cada uno segun su contrato: productos
 * (lectura y reserva de tiraje), inventario (entregas y elementos), ms-finanzas
 * (transacciones), ms-identidad (token de servicio y contacto), correo
 * (confirmacion de compra) y admin-parametros (tasas).
 *
 * <p>Cada uno es <b>idempotente como su contrato dice</b> (la misma clave
 * devuelve lo mismo sin repetir el efecto) y se le puede pedir que falle las
 * proximas N veces con 503, para probar el reintento; y cuenta los efectos de
 * verdad (unidades descontadas, entregas aplicadas, asientos, correos), que es
 * lo que las pruebas de duplicacion miran.
 */
final class ServiciosSimulados {

    static final String TOKEN_DE_LA_TIENDA = "token-de-servicio-de-la-tienda";

    private final HttpServer servidor;

    /** Productos del catalogo por id: el JSON y el tiraje vivo. */
    final Map<String, String> productos = new ConcurrentHashMap<>();
    final Map<String, AtomicInteger> tirajes = new ConcurrentHashMap<>();
    final Map<String, String> estados = new ConcurrentHashMap<>();

    /** Reservas por clave: el codigo y el estado que se respondio. */
    final Map<String, String> reservas = new ConcurrentHashMap<>();
    final AtomicInteger unidadesDescontadas = new AtomicInteger();
    final AtomicInteger llamadasDeReserva = new AtomicInteger();
    final AtomicInteger fallosDeReserva = new AtomicInteger();
    /** Otra venta se llevo la ultima unidad entre la lectura y la reserva. */
    volatile boolean agotarAlReservar = false;

    /** Entregas por clave (el cuerpo), las aplicadas y las llamadas. */
    final Map<String, String> entregas = new ConcurrentHashMap<>();
    final List<String> entregasAplicadas = new CopyOnWriteArrayList<>();
    final AtomicInteger llamadasDeEntrega = new AtomicInteger();
    final AtomicInteger fallosDeEntrega = new AtomicInteger();
    volatile int rechazoDeEntrega = 0;

    /** Asientos por refId. */
    final Map<String, String> asientos = new ConcurrentHashMap<>();
    final AtomicInteger llamadasDeAsiento = new AtomicInteger();
    final AtomicInteger fallosDeAsiento = new AtomicInteger();

    /** Correos por clave. */
    final Map<String, String> correos = new ConcurrentHashMap<>();
    final AtomicInteger llamadasDeCorreo = new AtomicInteger();
    final AtomicInteger fallosDeCorreo = new AtomicInteger();

    volatile String tasaUsd = null;
    final AtomicInteger tokensEmitidos = new AtomicInteger();
    final List<String> sinCredencial = new CopyOnWriteArrayList<>();
    final List<String> trazas = new CopyOnWriteArrayList<>();

    ServiciosSimulados() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        servidor.setExecutor(Executors.newFixedThreadPool(8));
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    String base() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    void parar() {
        servidor.stop(0);
    }

    void reiniciar() {
        productos.clear();
        tirajes.clear();
        estados.clear();
        reservas.clear();
        unidadesDescontadas.set(0);
        llamadasDeReserva.set(0);
        fallosDeReserva.set(0);
        agotarAlReservar = false;
        entregas.clear();
        entregasAplicadas.clear();
        llamadasDeEntrega.set(0);
        fallosDeEntrega.set(0);
        rechazoDeEntrega = 0;
        asientos.clear();
        llamadasDeAsiento.set(0);
        fallosDeAsiento.set(0);
        correos.clear();
        llamadasDeCorreo.set(0);
        fallosDeCorreo.set(0);
        tasaUsd = null;
        sinCredencial.clear();
        trazas.clear();
    }

    /** Un producto en venta: ACTIVO, con su precio en COP y su tiraje (-1 ilimitado). */
    void producto(String id, String nombre, String precio, int tiraje) {
        producto(id, nombre, precio, tiraje, null);
    }

    void producto(String id, String nombre, String precio, int tiraje, String promocion) {
        tirajes.put(id, new AtomicInteger(tiraje));
        estados.put(id, "ACTIVO");
        productos.put(id, """
                {"id":"%s","nombre":"%s","imagen":"img/%s.png","descripcion":"Descripcion de %s","tipo":"ARMA",\
                "tiraje":%%d,"precioMonedaReal":%s,"premium":false,"estado":"%%s"%s,\
                "creadoEn":"2026-09-01T10:00:00Z","modificadoEn":"2026-09-20T10:00:00Z"}"""
                .formatted(id, nombre, id, nombre, precio, promocion == null ? "" : ",\"promocion\":" + promocion));
    }

    private String jsonDe(String id) {
        return productos.get(id).formatted(tirajes.get(id).get(), estados.get(id));
    }

    // ------------------------------------------------------------------ HTTP

    private void atender(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath();
        String metodo = intercambio.getRequestMethod();
        String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String traceparent = intercambio.getRequestHeaders().getFirst("traceparent");
        if (traceparent != null) {
            trazas.add(ruta + " " + traceparent);
        }
        try {
            if (ruta.equals("/api/v1/auth/token")) {
                tokensEmitidos.incrementAndGet();
                responder(intercambio, 200, "{\"access_token\":\"" + TOKEN_DE_LA_TIENDA
                        + "\",\"token_type\":\"Bearer\",\"expires_in\":900}");
                return;
            }
            if (ruta.startsWith("/api/v1/parametros/")) {
                String clave = ruta.substring("/api/v1/parametros/".length(), ruta.length() - "/valor".length());
                String valor = clave.equals("tienda.tasa-cop-usd") && tasaUsd != null ? "\"" + tasaUsd + "\"" : "null";
                responder(intercambio, 200, "{\"clave\":\"" + clave + "\",\"valor\":" + valor + ",\"version\":1}");
                return;
            }
            if (ruta.equals("/api/v1/productos") && metodo.equals("GET")) {
                List<String> todos = new ArrayList<>();
                productos.keySet().stream().sorted().forEach(id -> todos.add(jsonDe(id)));
                responder(intercambio, 200, "{\"content\":[" + String.join(",", todos)
                        + "],\"page\":0,\"size\":50,\"totalElements\":" + todos.size() + ",\"totalPages\":1}");
                return;
            }
            if (ruta.startsWith("/api/v1/productos/") && ruta.endsWith("/adquisiciones")) {
                exigirCredencial(intercambio, ruta);
                reservar(intercambio, ruta.substring("/api/v1/productos/".length(),
                        ruta.length() - "/adquisiciones".length()));
                return;
            }
            if (ruta.startsWith("/api/v1/productos/") && metodo.equals("GET")) {
                String id = ruta.substring("/api/v1/productos/".length());
                if (productos.containsKey(id)) {
                    responder(intercambio, 200, jsonDe(id));
                } else {
                    responder(intercambio, 404, "{\"status\":404}");
                }
                return;
            }
            if (ruta.equals("/api/v1/inventario/entregas")) {
                exigirCredencial(intercambio, ruta);
                entregar(intercambio, cuerpo);
                return;
            }
            if (ruta.equals("/api/v1/inventario/elementos")) {
                exigirCredencial(intercambio, ruta);
                String jugador = intercambio.getRequestHeaders().getFirst("X-User-Name");
                List<String> elementos = new ArrayList<>();
                for (String entrega : entregasAplicadas) {
                    if (Objects.equals(JsonPath.read(entrega, "$.uid"), jugador)) {
                        List<String> ids = JsonPath.read(entrega, "$.productos[*].productoId");
                        ids.forEach(id -> elementos.add("{\"productoId\":\"" + id + "\"}"));
                    }
                }
                responder(intercambio, 200, "{\"elementos\":[" + String.join(",", elementos)
                        + "],\"numero\":0,\"tamanio\":16,\"totalElementos\":" + elementos.size()
                        + ",\"totalPaginas\":1,\"ultima\":true}");
                return;
            }
            if (ruta.equals("/api/v1/transacciones")) {
                exigirCredencial(intercambio, ruta);
                llamadasDeAsiento.incrementAndGet();
                if (fallosDeAsiento.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                    responder(intercambio, 503, "{}");
                    return;
                }
                String refId = JsonPath.read(cuerpo, "$.refId");
                if (asientos.putIfAbsent(refId, cuerpo) != null) {
                    responder(intercambio, 409, "{\"type\":\"https://nexusbattles.upb.edu.co/errors/transaccion-ya-registrada\"}");
                } else {
                    responder(intercambio, 201, "{}");
                }
                return;
            }
            if (ruta.startsWith("/api/v1/internal/usuarios/") && ruta.endsWith("/contacto")) {
                exigirCredencial(intercambio, ruta);
                String uid = ruta.substring("/api/v1/internal/usuarios/".length(), ruta.length() - "/contacto".length());
                responder(intercambio, 200, "{\"uid\":\"" + uid + "\",\"email\":\"comprador@nexus.test\","
                        + "\"apodo\":\"comprador\",\"estado\":\"ACTIVO\"}");
                return;
            }
            if (ruta.equals("/api/v1/correos/confirmacion-compra")) {
                exigirCredencial(intercambio, ruta);
                llamadasDeCorreo.incrementAndGet();
                if (fallosDeCorreo.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                    responder(intercambio, 503, "{}");
                    return;
                }
                correos.putIfAbsent(intercambio.getRequestHeaders().getFirst("Idempotency-Key"), cuerpo);
                responder(intercambio, 202, "");
                return;
            }
            responder(intercambio, 404, "{\"status\":404}");
        } catch (SinCredencial sin) {
            responder(intercambio, 401, "{}");
        }
    }

    private void reservar(HttpExchange intercambio, String id) throws IOException {
        llamadasDeReserva.incrementAndGet();
        if (fallosDeReserva.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            responder(intercambio, 503, "{}");
            return;
        }
        String clave = intercambio.getRequestHeaders().getFirst("Idempotency-Key");
        if (clave == null || clave.isBlank()) {
            responder(intercambio, 400, "{}");
            return;
        }
        if (!productos.containsKey(id)) {
            responder(intercambio, 404, "{}");
            return;
        }
        String previa = reservas.get(clave);
        if (previa == null) {
            synchronized (this) {
                previa = reservas.get(clave);
                if (previa == null) {
                    AtomicInteger tiraje = tirajes.get(id);
                    if (agotarAlReservar) {
                        previa = "409 AGOTADO";
                    } else if ("SUSPENDIDO".equals(estados.get(id))) {
                        previa = "409 SUSPENDIDO";
                    } else if (tiraje.get() == -1) {
                        previa = "200 ACEPTADA";
                    } else if (tiraje.get() > 0) {
                        tiraje.decrementAndGet();
                        unidadesDescontadas.incrementAndGet();
                        previa = "200 ACEPTADA";
                    } else {
                        previa = "409 AGOTADO";
                    }
                    reservas.put(clave, previa);
                }
            }
        }
        String[] partes = previa.split(" ");
        responder(intercambio, Integer.parseInt(partes[0]), "{\"estado\":\"" + partes[1] + "\",\"mensaje\":\"-\"}");
    }

    private void entregar(HttpExchange intercambio, String cuerpo) throws IOException {
        llamadasDeEntrega.incrementAndGet();
        if (fallosDeEntrega.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            responder(intercambio, 503, "{}");
            return;
        }
        if (rechazoDeEntrega != 0) {
            responder(intercambio, rechazoDeEntrega, "{}");
            return;
        }
        String clave = intercambio.getRequestHeaders().getFirst("Idempotency-Key");
        String previa = entregas.putIfAbsent(clave, cuerpo);
        if (previa == null) {
            entregasAplicadas.add(cuerpo);
            responder(intercambio, 201, "{\"id\":\"e-1\"}");
        } else if (previa.equals(cuerpo)) {
            responder(intercambio, 200, "{\"id\":\"e-1\"}");
        } else {
            responder(intercambio, 409, "{}");
        }
    }

    private void exigirCredencial(HttpExchange intercambio, String ruta) {
        String autorizacion = intercambio.getRequestHeaders().getFirst("Authorization");
        if (!("Bearer " + TOKEN_DE_LA_TIENDA).equals(autorizacion)) {
            sinCredencial.add(ruta);
            throw new SinCredencial();
        }
    }

    private static void responder(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }

    private static final class SinCredencial extends RuntimeException {
    }
}
