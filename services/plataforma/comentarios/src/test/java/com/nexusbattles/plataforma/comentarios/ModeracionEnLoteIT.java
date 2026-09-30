package com.nexusbattles.plataforma.comentarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.comentarios.moderacion.AsientoRepository;

/**
 * HU-COM-008, CA-02 contra PostgreSQL de verdad: el lote aplicado deja estado y
 * asientos en la base, y el lote rechazado no deja NADA. Lo que una prueba con
 * repositorios en memoria no puede afirmar es lo de la transaccion real.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "comentarios.imagenes.limpieza-activa=false"
        })
@DisplayName("HU-COM-008 CA-02: el lote contra PostgreSQL")
class ModeracionEnLoteIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    static VecinosDePrueba vecinos;

    @BeforeAll
    static void levantarVecinos() throws Exception {
        vecinos = VecinosDePrueba.arrancar();
    }

    @AfterAll
    static void apagarVecinos() {
        if (vecinos != null) {
            vecinos.close();
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
        vecinos.registrar(registro);
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private AsientoRepository asientos;

    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();
    private final UUID uidModeradora = UUID.randomUUID();

    private HttpResponse<String> pedir(String metodo, String ruta, String token, String json) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, json == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json));
        if (json != null) {
            peticion.header("Content-Type", "application/json");
        }
        if (token != null) {
            peticion.header("Authorization", token);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String moderadora() {
        return "Bearer " + emisor.tokenDeUsuario("AdaLaJusta", uidModeradora, "MODERADOR");
    }

    private String comentar(String producto, String texto) throws Exception {
        UUID uid = UUID.randomUUID();
        String token = "Bearer " + emisor.tokenDeJugador("Jugador-" + uid.toString().substring(0, 6), uid);
        HttpResponse<String> r = pedir("POST", "/api/v1/products/" + producto + "/comments", token,
                "{\"texto\":\"" + texto + "\",\"imagenes\":[]}");
        assertEquals(201, r.statusCode(), r.body());
        return JsonPath.read(r.body(), "$.id");
    }

    private String estadoEnBase(String id) {
        return jdbc.queryForObject("select estado from comentarios.comentarios where id = ?", String.class, id);
    }

    private String productoNuevo() {
        String id = UUID.randomUUID().toString();
        vecinos.productos.add(id);
        return id;
    }

    private static String lote(String accion, List<String> ids) {
        return "{\"accion\":\"" + accion + "\",\"motivo\":\"limpieza del lote\",\"comentarioIds\":[\""
                + String.join("\",\"", ids) + "\"]}";
    }

    @Test
    @DisplayName("un lote valido deja todos los estados y un asiento por comentario en la base")
    void loteAplicado() throws Exception {
        String producto = productoNuevo();
        String a = comentar(producto, "uno");
        String b = comentar(producto, "dos");
        String c = comentar(producto, "tres");

        HttpResponse<String> r = pedir("POST", "/api/v1/comentarios/moderacion/decisiones", moderadora(),
                lote("OCULTAR", List.of(a, b, c)));

        assertEquals(200, r.statusCode(), r.body());
        assertEquals(3, (Integer) JsonPath.read(r.body(), "$.total"));
        for (String id : List.of(a, b, c)) {
            assertEquals("OCULTO", estadoEnBase(id));
            var asiento = asientos.findByComentarioIdOrderByFechaAsc(id);
            assertEquals(1, asiento.size());
            assertEquals(uidModeradora.toString(), asiento.get(0).moderadorId());
            assertEquals("limpieza del lote", asiento.get(0).motivo());
        }
    }

    @Test
    @DisplayName("un lote con un comentario invalido no cambia nada en la base: ni estados ni asientos")
    void loteRechazado() throws Exception {
        String producto = productoNuevo();
        String a = comentar(producto, "uno");
        String b = comentar(producto, "dos");
        // b queda ELIMINADO: OCULTAR ya no vale sobre el.
        assertEquals(200, pedir("POST", "/api/v1/comentarios/moderacion/" + b + "/decision", moderadora(),
                "{\"accion\":\"ELIMINAR\",\"motivo\":\"preparar la prueba\"}").statusCode());
        long asientosAntes = asientos.count();

        HttpResponse<String> r = pedir("POST", "/api/v1/comentarios/moderacion/decisiones", moderadora(),
                lote("OCULTAR", List.of(a, b, "no-existe")));

        assertEquals(409, r.statusCode(), r.body());
        assertEquals("LOTE_RECHAZADO", JsonPath.read(r.body(), "$.motivo"));
        assertEquals(List.of(b, "no-existe"), JsonPath.read(r.body(), "$.fallidos[*].comentarioId"));
        assertEquals(List.of("TRANSICION_INVALIDA", "COMENTARIO_NO_ENCONTRADO"),
                JsonPath.read(r.body(), "$.fallidos[*].motivo"));

        assertEquals("PUBLICADO", estadoEnBase(a), "el valido tampoco cambio");
        assertEquals("ELIMINADO", estadoEnBase(b));
        assertEquals(asientosAntes, asientos.count(), "no se escribio ningun asiento");
        assertTrue(asientos.findByComentarioIdOrderByFechaAsc(a).isEmpty());
    }
}
