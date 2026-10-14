package com.nexusbattles.plataforma.notificaciones.politicas;

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
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * SA-11 (§7.3.7) de punta a punta dentro de notificaciones: la aplicacion
 * completa (Flyway V4 sobre PostgreSQL, JPA validando el mapeo, las
 * sentencias nativas del cursor, la cadena de seguridad y las dos fuentes de
 * avisos juntas) contra un servidor HTTP del JDK que hace de admin-parametros
 * ({@code GET /api/v1/parametros/politicas/cambios/posteriores}).
 *
 * <p>El primer ingreso lee la ventana y trae el cambio publicado ayer; la
 * sesion siguiente lee con solape y no lo duplica; con admin-parametros caido
 * la entrega sigue respondiendo 200. Las corre el CI: necesitan Docker.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                // Sin intervalo: el recorrido consulta varias veces seguidas.
                "notificaciones.politicas.intervalo-minimo-s=0"
        }
)
@DisplayName("Notificaciones · avisos de cambios en las politicas de punta a punta")
class AvisosDePoliticasIT {

    /** Publicado ayer: dentro de la ventana de 30 dias del primer ingreso. */
    private static final Instant PUBLICADO = Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.SECONDS);
    private static final Instant VIGENTE = PUBLICADO.plus(Duration.ofDays(6));

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static HttpServer parametros;
    private static final List<String> consultas = new CopyOnWriteArrayList<>();
    private static final List<String> portadores = new CopyOnWriteArrayList<>();
    private static volatile int estadoDeParametros = 200;

    @DynamicPropertySource
    static void vecinosFalsos(DynamicPropertyRegistry registro) throws IOException {
        EmisorDeTokensDePrueba.registrarJwks(registro);
        parametros = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        parametros.createContext("/api/v1/parametros/politicas/cambios/posteriores", AvisosDePoliticasIT::posteriores);
        parametros.start();
        registro.add("notificaciones.politicas.parametros-url",
                () -> "http://127.0.0.1:" + parametros.getAddress().getPort() + "/api/v1");
    }

    @AfterAll
    static void apagar() {
        if (parametros != null) {
            parametros.stop(0);
        }
    }

    private static void posteriores(HttpExchange intercambio) throws IOException {
        String consulta = intercambio.getRequestURI().getRawQuery();
        consultas.add(consulta == null ? "" : URLDecoder.decode(consulta, StandardCharsets.UTF_8));
        portadores.add(String.valueOf(intercambio.getRequestHeaders().getFirst("Authorization")));
        if (estadoDeParametros != 200) {
            responder(intercambio, estadoDeParametros, "{\"status\":" + estadoDeParametros + "}");
            return;
        }
        String desde = parametro(consulta, "desde");
        if (desde != null && Instant.parse(desde).isBefore(PUBLICADO)) {
            responder(intercambio, 200, """
                    {"hasta":"%s","completo":true,"cambios":[
                      {"id":"cambio-it-1","politica":"POLITICA_DE_PRIVACIDAD",
                       "nombreDeLaPolitica":"Política de privacidad","titulo":"Datos del chat",
                       "descripcion":"Los mensajes del chat se guardan noventa días y luego se borran.",
                       "vigenteDesde":"%s","publicadoEn":"%s"}]}
                    """.formatted(PUBLICADO, VIGENTE, PUBLICADO));
        } else {
            responder(intercambio, 200, """
                    {"hasta":"%s","completo":true,"cambios":[]}
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
    @DisplayName("el cambio publicado llega en el primer ingreso, una sola vez, y sin admin-parametros la entrega sigue")
    void recorridoCompleto() throws Exception {
        UUID ana = UUID.randomUUID();
        String token = emisor.tokenDeJugador("Ana", ana);

        // 1. Primer ingreso: la ventana de 30 dias trae el cambio de ayer.
        HttpResponse<String> primera = pendientes(ana, token, "escritorio");
        assertEquals(200, primera.statusCode(), primera.body());
        assertTrue(primera.body().contains("\"id\":\"politica:cambio-it-1\""), primera.body());
        assertTrue(primera.body().contains("\"tipo\":\"CAMBIO_POLITICA\""), primera.body());
        assertTrue(primera.body().contains("\"titulo\":\"Actualizamos la política de privacidad\""), primera.body());
        assertTrue(primera.body().contains("Datos del chat. Los mensajes del chat se guardan noventa días y luego"
                + " se borran. Rige desde el "), primera.body());
        assertTrue(primera.body().contains("\"creadaEn\":\"" + PUBLICADO + "\""), primera.body());
        Instant ventana = Instant.parse(consultas.getFirst().replaceAll("^desde=([^&]+)&limite=50$", "$1"));
        assertTrue(Duration.between(ventana, Instant.now()).toDays() >= 29, "leyo la ventana: " + consultas);
        assertTrue(portadores.stream().allMatch("null"::equals), "la lectura es publica: " + portadores);

        // 2. Siguiente sesion: desde un minuto antes del cursor; lo repetido no se duplica.
        assertEquals(200, pendientes(ana, token, "movil").statusCode());
        assertTrue(consultas.contains("desde=" + PUBLICADO.minus(Duration.ofMinutes(1)) + "&limite=50"),
                "leyo con solape desde el cursor: " + consultas);
        HttpResponse<String> bandeja = bandeja(ana, token);
        assertEquals(200, bandeja.statusCode(), bandeja.body());
        assertEquals(1, ocurrencias(bandeja.body(), "politica:cambio-it-1"), bandeja.body());

        // 3. Admin-parametros caido: la entrega de pendientes no se entera.
        estadoDeParametros = 503;
        try {
            HttpResponse<String> sinParametros = pendientes(ana, token, "otra-pestana");
            assertEquals(200, sinParametros.statusCode(), sinParametros.body());
        } finally {
            estadoDeParametros = 200;
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
