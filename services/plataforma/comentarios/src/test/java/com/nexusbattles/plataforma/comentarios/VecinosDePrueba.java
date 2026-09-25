package com.nexusbattles.plataforma.comentarios;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.test.context.DynamicPropertyRegistry;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Los tres servicios con los que habla comentarios, de mentira pero por HTTP
 * de verdad: el catalogo de productos, la consulta de sanciones y la lista
 * negra. Con las formas exactas de sus contratos (productos.yaml,
 * moderacion-sanciones-consulta.yaml, moderacion-lista-negra.yaml 2.0.0).
 *
 * <p>Un servidor HTTP del JDK y no un simulacro del cliente: asi las pruebas de
 * integracion recorren tambien los {@code RestClient} reales del servicio, con
 * sus tiempos, su deserializacion y su manejo de errores.
 */
public final class VecinosDePrueba implements AutoCloseable {

    private final HttpServer servidor;

    /** Productos que el catalogo dice tener. */
    public final Set<String> productos = ConcurrentHashMap.newKeySet();

    /** Uids con sancion activa. */
    public final Set<String> sancionados = ConcurrentHashMap.newKeySet();

    /** Si el catalogo responde 503 a todo. */
    public final AtomicBoolean catalogoCaido = new AtomicBoolean();

    /** Los cuerpos que le llegaron a la lista negra, para mirar el contexto. */
    public final List<String> verificaciones = new CopyOnWriteArrayList<>();

    /** Cuantas veces se pregunto al catalogo (para ver la cache). */
    public final List<String> consultasAlCatalogo = new CopyOnWriteArrayList<>();

    private VecinosDePrueba(HttpServer servidor) {
        this.servidor = servidor;
    }

    public static VecinosDePrueba arrancar() throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        VecinosDePrueba vecinos = new VecinosDePrueba(servidor);
        servidor.createContext("/api/v1/productos/", vecinos::catalogo);
        servidor.createContext("/api/v1/sanciones/usuarios/", vecinos::sanciones);
        servidor.createContext("/api/v1/lista-negra/verificar", vecinos::listaNegra);
        // Varios hilos: las pruebas de concurrencia mandan peticiones a la vez
        // y un solo hilo las pondria en fila antes de llegar a la base.
        servidor.setExecutor(Executors.newCachedThreadPool());
        servidor.start();
        return vecinos;
    }

    /** Apunta los tres clientes del servicio a este servidor. */
    public void registrar(DynamicPropertyRegistry registro) {
        String base = "http://127.0.0.1:" + servidor.getAddress().getPort();
        registro.add("comentarios.productos.url", () -> base);
        registro.add("comentarios.sanciones.url", () -> base + "/api/v1");
        registro.add("comentarios.lista-negra.url", () -> base + "/api/v1/lista-negra/verificar");
    }

    private void catalogo(HttpExchange intercambio) throws IOException {
        String id = intercambio.getRequestURI().getPath().substring("/api/v1/productos/".length());
        consultasAlCatalogo.add(id);
        if (catalogoCaido.get()) {
            responder(intercambio, 503, "{\"status\":503}");
        } else if (productos.contains(id)) {
            responder(intercambio, 200, "{\"id\":\"" + id + "\",\"nombre\":\"Producto de prueba\",\"tipo\":\"ARMA\"}");
        } else {
            responder(intercambio, 404, "{\"type\":\"urn:nexus:problema:producto-no-encontrado\",\"status\":404}");
        }
    }

    private void sanciones(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath();
        String uid = ruta.substring("/api/v1/sanciones/usuarios/".length(), ruta.length() - "/activa".length());
        boolean activa = sancionados.contains(uid);
        responder(intercambio, 200, "{\"sancionActiva\":" + activa + ",\"motivo\":"
                + (activa ? "\"Spam reiterado\"" : "null") + ",\"vigenteHasta\":null}");
    }

    /** Senala todo texto que contenga «prohibido»: la politica de COMENTARIO es REVISION. */
    private void listaNegra(HttpExchange intercambio) throws IOException {
        String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        verificaciones.add(cuerpo);
        boolean coincide = cuerpo.contains("prohibido");
        responder(intercambio, 200, coincide
                ? "{\"aprobado\":false,\"accion\":\"REVISION\",\"motivo\":\"contenido no permitido\"}"
                : "{\"aprobado\":true,\"accion\":\"PERMITIR\"}");
    }

    private static void responder(HttpExchange intercambio, int estado, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
