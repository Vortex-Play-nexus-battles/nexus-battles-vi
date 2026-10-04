package com.nexusbattles.ms_identidad.onboarding;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Un servidor HTTP de verdad (el del JDK) que hace de ms-finanzas, inventario,
 * productos, admin-parametros o auditoria en las pruebas del alta.
 *
 * <p>De verdad, y no un mock del RestClient, para que las pruebas pasen por
 * lo mismo que produccion: serializacion, cabeceras, codigos de estado,
 * tiempos de espera y conexion rechazada. Guarda cada peticion para poder
 * afirmar que se envio, con que cabeceras y cuantas veces.
 */
public final class ServidorFalso implements AutoCloseable {

    public record Peticion(String metodo, String ruta, String consulta, Map<String, String> cabeceras, String cuerpo) {

        public String cabecera(String nombre) {
            return cabeceras.get(nombre.toLowerCase(Locale.ROOT));
        }
    }

    public record Respuesta(int estado, String cuerpo) {
    }

    private record Regla(String metodo, Pattern ruta, Function<Peticion, Respuesta> respuesta) {
    }

    private final HttpServer servidor;
    private final ExecutorService hilos = Executors.newCachedThreadPool();
    private final List<Peticion> recibidas = new CopyOnWriteArrayList<>();
    private final List<Regla> reglas = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> contadores = new ConcurrentHashMap<>();

    public ServidorFalso() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        servidor.createContext("/", this::atender);
        servidor.setExecutor(hilos);
        servidor.start();
    }

    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    /** La ultima regla que coincide gana: se puede cambiar la respuesta a mitad de prueba. */
    public ServidorFalso responder(String metodo, String rutaRegex, Function<Peticion, Respuesta> respuesta) {
        reglas.add(new Regla(metodo, Pattern.compile(rutaRegex), respuesta));
        return this;
    }

    public ServidorFalso responder(String metodo, String rutaRegex, int estado, String cuerpo) {
        return responder(metodo, rutaRegex, peticion -> new Respuesta(estado, cuerpo));
    }

    public List<Peticion> recibidas() {
        return List.copyOf(recibidas);
    }

    public List<Peticion> recibidas(String metodo, String rutaRegex) {
        Pattern patron = Pattern.compile(rutaRegex);
        List<Peticion> coincidentes = new ArrayList<>();
        for (Peticion peticion : recibidas) {
            if (peticion.metodo().equals(metodo) && patron.matcher(peticion.ruta()).matches()) {
                coincidentes.add(peticion);
            }
        }
        return coincidentes;
    }

    /** Contador por clave, para respuestas que cambian con cada llamada. */
    public int siguiente(String clave) {
        return contadores.merge(clave, 1, Integer::sum);
    }

    private void atender(HttpExchange intercambio) throws IOException {
        try (intercambio) {
            Map<String, String> cabeceras = new ConcurrentHashMap<>();
            intercambio.getRequestHeaders().forEach((nombre, valores) -> {
                if (!valores.isEmpty()) {
                    cabeceras.put(nombre.toLowerCase(Locale.ROOT), valores.get(0));
                }
            });
            String cuerpo;
            try (InputStream entrada = intercambio.getRequestBody()) {
                cuerpo = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
            }
            Peticion peticion = new Peticion(intercambio.getRequestMethod(), intercambio.getRequestURI().getPath(),
                    intercambio.getRequestURI().getQuery(), cabeceras, cuerpo);
            recibidas.add(peticion);

            Respuesta respuesta = new Respuesta(404, "{\"title\":\"sin regla en ServidorFalso\"}");
            for (int i = reglas.size() - 1; i >= 0; i--) {
                Regla regla = reglas.get(i);
                if (regla.metodo().equals(peticion.metodo()) && regla.ruta().matcher(peticion.ruta()).matches()) {
                    respuesta = regla.respuesta().apply(peticion);
                    break;
                }
            }
            byte[] bytes = respuesta.cuerpo() == null ? new byte[0] : respuesta.cuerpo().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().set("Content-Type",
                    respuesta.estado() >= 400 ? "application/problem+json" : "application/json");
            intercambio.sendResponseHeaders(respuesta.estado(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream salida = intercambio.getResponseBody()) {
                    salida.write(bytes);
                }
            }
        }
    }

    @Override
    public void close() {
        servidor.stop(0);
        hilos.shutdownNow();
    }
}
