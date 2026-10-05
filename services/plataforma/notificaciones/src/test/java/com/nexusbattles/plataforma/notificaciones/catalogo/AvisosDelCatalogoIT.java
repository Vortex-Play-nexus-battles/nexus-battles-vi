package com.nexusbattles.plataforma.notificaciones.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * HU-NOT-001 (#532) de punta a punta dentro de notificaciones: la aplicacion
 * COMPLETA (Flyway V3 sobre PostgreSQL, JPA validando el mapeo, la cadena de
 * seguridad, la credencial de servicio de verdad) contra un servidor HTTP del
 * JDK que hace de ms-identidad (el token de servicio) y de productos
 * ({@code GET /api/v1/productos/alertas/cambios}, productos.yaml 1.6.0).
 *
 * <p>Lo que solo se puede afirmar aqui: que el primer ingreso deja la linea
 * base sin avisos, que un cambio posterior llega a la bandeja como
 * {@code CAMBIO_CATALOGO} con su descripcion y su fecha por
 * {@code POST .../pending}, que repetirlo no lo duplica (id idempotente y
 * cursor que solo avanza, con las sentencias nativas de verdad) y que con
 * productos caido la entrega sigue respondiendo 200. Las corre el CI: necesitan
 * Docker.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                // Sin intervalo: el recorrido consulta varias veces seguidas.
                "notificaciones.catalogo.intervalo-minimo-s=0"
        }
)
@DisplayName("Notificaciones · avisos del catalogo de punta a punta")
class AvisosDelCatalogoIT {

    /** La linea base que da productos y el cambio que se registra despues de ella. */
    private static final String LINEA_BASE = "2026-10-05T10:00:00Z";
    private static final String CAMBIO_EN = "2026-10-05T15:30:00Z";
    private static final String TOKEN_DE_SERVICIO = "token-de-servicio-de-notificaciones";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static HttpServer vecinos;
    private static final List<String> consultas = new CopyOnWriteArrayList<>();
    private static final List<String> portadores = new CopyOnWriteArrayList<>();
    private static volatile int estadoDeProductos = 200;

    @DynamicPropertySource
    static void vecinosFalsos(DynamicPropertyRegistry registro) throws IOException {
        EmisorDeTokensDePrueba.registrarJwks(registro);
        vecinos = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // ms-identidad: client_credentials (ADR-005).
        vecinos.createContext("/api/v1/auth/token", intercambio -> responder(intercambio, 200, """
                {"access_token":"%s","token_type":"Bearer","expires_in":300}
                """.formatted(TOKEN_DE_SERVICIO)));
        // productos: sin desde, la linea base; desde la linea base, un cambio.
        vecinos.createContext("/api/v1/productos/alertas/cambios", AvisosDelCatalogoIT::productos);
        vecinos.start();

        String base = "http://127.0.0.1:" + vecinos.getAddress().getPort();
        registro.add("notificaciones.catalogo.productos-url", () -> base);
        registro.add("seguridad.servicio.url", () -> base + "/api/v1/auth/token");
        registro.add("seguridad.servicio.client-id", () -> "notificaciones");
        registro.add("seguridad.servicio.client-secret", () -> "secreto-de-prueba");
    }

    @AfterAll
    static void apagar() {
        if (vecinos != null) {
            vecinos.stop(0);
        }
    }

    private static void productos(HttpExchange intercambio) throws IOException {
        String consulta = intercambio.getRequestURI().getRawQuery();
        consultas.add(consulta == null ? "" : URLDecoder.decode(consulta, StandardCharsets.UTF_8));
        portadores.add(String.valueOf(intercambio.getRequestHeaders().getFirst("Authorization")));
        if (estadoDeProductos != 200) {
            responder(intercambio, estadoDeProductos, "{\"status\":" + estadoDeProductos + "}");
            return;
        }
        String desde = parametro(consulta, "desde");
        if (desde == null) {
            responder(intercambio, 200, """
                    {"hasta":"%s","completo":true,"alertas":[]}
                    """.formatted(LINEA_BASE));
        } else if (Instant.parse(desde).isBefore(Instant.parse(CAMBIO_EN))) {
            responder(intercambio, 200, """
                    {"hasta":"%s","completo":true,"alertas":[
                      {"id":"alerta-it-1","productoId":"producto-1","productoNombre":"Espada solar",
                       "tipo":"PRODUCTO_MODIFICADO","descripcion":"El producto Espada solar fue modificado.",
                       "implementadaEn":"%s"}]}
                    """.formatted(CAMBIO_EN, CAMBIO_EN));
        } else {
            responder(intercambio, 200, """
                    {"hasta":"%s","completo":true,"alertas":[]}
                    """.formatted(desde));
        }
    }

    private static String parametro(String consulta, String nombre) {
        if (consulta == null) {
            return null;
        }
        for (String par : consulta.split("&")) {
            String[] partes = par.split("=", 2);
            if (partes.length == 2 && partes[0].equals(nombre)) {
                return URLDecoder.decode(partes[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void responder(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, bytes.length);
        intercambio.getResponseBody().write(bytes);
        intercambio.close();
    }

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int puerto;

    @Test
    @DisplayName("linea base sin avisos, el cambio posterior llega una sola vez con su fecha, y sin productos la entrega sigue")
    void recorridoCompleto() throws Exception {
        UUID ana = UUID.randomUUID();
        String token = emisor.tokenDeJugador("Ana", ana);

        // 1. Primer ingreso: la linea base, ningun aviso del historial.
        HttpResponse<String> primera = pendientes(ana, token, "escritorio");
        assertEquals(200, primera.statusCode(), primera.body());
        assertEquals("[]", primera.body().strip(), "el primer ingreso no vuelca el historial del catalogo");
        assertTrue(consultas.stream().anyMatch(c -> c.equals("limite=50")), "pidio la linea base: " + consultas);

        // 2. Siguiente sesion: el cambio posterior entra como CAMBIO_CATALOGO.
        HttpResponse<String> segunda = pendientes(ana, token, "movil");
        assertEquals(200, segunda.statusCode(), segunda.body());
        assertTrue(segunda.body().contains("\"id\":\"catalogo:alerta-it-1\""), segunda.body());
        assertTrue(segunda.body().contains("\"tipo\":\"CAMBIO_CATALOGO\""), segunda.body());
        assertTrue(segunda.body().contains("\"titulo\":\"Producto modificado\""), segunda.body());
        assertTrue(segunda.body().contains("El producto Espada solar fue modificado."), segunda.body());
        assertTrue(segunda.body().contains("Fecha de implementación: 5 de octubre de 2026, 10:30."), segunda.body());
        assertTrue(segunda.body().contains("\"creadaEn\":\"" + CAMBIO_EN + "\""), segunda.body());
        assertTrue(consultas.contains("desde=" + LINEA_BASE + "&limite=50"),
                "pidio desde el cursor que dejo la linea base: " + consultas);
        assertTrue(portadores.stream().allMatch(("Bearer " + TOKEN_DE_SERVICIO)::equals),
                "productos recibio la credencial de servicio: " + portadores);

        // 3. Otra vez: ni el cursor retrocede ni el aviso se duplica.
        assertEquals(200, pendientes(ana, token, "tableta").statusCode());
        HttpResponse<String> bandeja = bandeja(ana, token);
        assertEquals(200, bandeja.statusCode(), bandeja.body());
        assertEquals(1, ocurrencias(bandeja.body(), "catalogo:alerta-it-1"), bandeja.body());
        assertTrue(bandeja.body().contains("\"noLeidas\":1"), bandeja.body());

        // 4. Productos caido: la entrega de pendientes no se entera.
        estadoDeProductos = 503;
        try {
            HttpResponse<String> sinProductos = pendientes(ana, token, "otra-pestana");
            assertEquals(200, sinProductos.statusCode(), sinProductos.body());
        } finally {
            estadoDeProductos = 200;
        }
    }

    private HttpResponse<String> pendientes(UUID dueno, String token, String sesion) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + puerto + "/api/v1/users/" + dueno + "/sessions/" + sesion + "/pending"))
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> bandeja(UUID dueno, String token) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + puerto + "/api/v1/users/" + dueno + "/notifications"))
                        .header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static int ocurrencias(String texto, String buscado) {
        Matcher m = Pattern.compile(Pattern.quote(buscado)).matcher(texto);
        int cuenta = 0;
        while (m.find()) {
            cuenta++;
        }
        return cuenta;
    }
}
