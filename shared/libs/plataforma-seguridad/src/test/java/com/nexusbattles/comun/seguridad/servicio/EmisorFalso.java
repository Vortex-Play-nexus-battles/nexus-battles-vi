package com.nexusbattles.comun.seguridad.servicio;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Emisor de tokens de mentira para las pruebas unitarias del cliente: solo
 * el endpoint {@code /protocol/openid-connect/token}, con el JDK y nada mas.
 *
 * <p>Responde un token distinto en cada peticion ({@code t1}, {@code t2}...)
 * para que se pueda distinguir «reutilizo el que tenia» de «pedi otro».
 */
final class EmisorFalso implements AutoCloseable {

    record Peticion(String authorization, String cuerpo) {
    }

    private final HttpServer servidor;
    private final AtomicInteger emitidos = new AtomicInteger();
    private final List<Peticion> peticiones = new CopyOnWriteArrayList<>();
    private volatile int expiraEnSegundos = 300;
    private volatile int estado = 200;

    EmisorFalso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/realms/prueba/protocol/openid-connect/token", intercambio -> {
            String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            peticiones.add(new Peticion(intercambio.getRequestHeaders().getFirst("Authorization"), cuerpo));

            byte[] respuesta;
            if (estado == 200) {
                respuesta = ("{\"access_token\":\"t" + emitidos.incrementAndGet()
                        + "\",\"token_type\":\"Bearer\",\"expires_in\":" + expiraEnSegundos + "}")
                        .getBytes(StandardCharsets.UTF_8);
            } else {
                respuesta = "{\"error\":\"invalid_client\"}".getBytes(StandardCharsets.UTF_8);
            }
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, respuesta.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(respuesta);
            }
        });
        servidor.start();
    }

    String urlDelRealm() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/realms/prueba";
    }

    void expiraEn(int segundos) {
        this.expiraEnSegundos = segundos;
    }

    void rechazarCredenciales() {
        this.estado = 401;
    }

    int emitidos() {
        return emitidos.get();
    }

    List<Peticion> peticiones() {
        return peticiones;
    }

    static String basic(String clientId, String secreto) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + secreto).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
