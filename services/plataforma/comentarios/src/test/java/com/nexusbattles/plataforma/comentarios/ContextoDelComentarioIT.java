package com.nexusbattles.plataforma.comentarios;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

/**
 * El comentario en su contexto contra PostgreSQL de verdad — contrato 1.17.0
 * (SA-16, §7.3.8).
 *
 * <p>Lo que los dobles no comprueban: que las dos consultas derivadas con su
 * proyeccion arrancan contra el esquema real, que la base corta y ordena por
 * cercania (antes y despues de la fecha del comentario) sin mezclar productos,
 * y que la ruta responde con la forma del contrato.
 *
 * <p>Requiere Docker (Testcontainers).
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "comentarios.imagenes.limpieza-activa=false"
        })
@DisplayName("1.17.0: el comentario en su contexto contra PostgreSQL")
class ContextoDelComentarioIT {

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

    private static final UUID UID_MODERADORA = UUID.fromString("22222222-3333-4444-5555-666666666666");

    @LocalServerPort
    private int puerto;

    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    private String comentario(String producto, String apodo, String estado, String cuando) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into comentarios.comentarios
                    (id, producto_id, autor_id, apodo_autor, texto, fecha_publicacion, estado)
                values (?, ?, ?, ?, ?, cast(? as timestamptz), ?)
                """, id, producto, UUID.randomUUID().toString(), apodo, "Dice " + apodo, cuando, estado);
        return id;
    }

    @Test
    @DisplayName("los de antes y los de despues, por cercania y del mas antiguo al mas reciente, solo de su producto")
    void contextoDeVerdad() throws Exception {
        String producto = "producto-contexto-" + UUID.randomUUID();
        String primero = comentario(producto, "Ana", "PUBLICADO", "2026-10-10T10:00:00Z");
        String segundo = comentario(producto, "Bruno", "OCULTO", "2026-10-10T11:00:00Z");
        String tercero = comentario(producto, "Carla", "PUBLICADO", "2026-10-10T12:00:00Z");
        String mirado = comentario(producto, "Dario", "EN_REVISION", "2026-10-10T13:00:00Z");
        String quinto = comentario(producto, "Elena", "ELIMINADO", "2026-10-10T14:00:00Z");
        comentario(producto, "Fabio", "PUBLICADO", "2026-10-10T15:00:00Z");
        comentario(producto, "Gala", "PUBLICADO", "2026-10-10T16:00:00Z");
        comentario("otro-" + producto, "Hugo", "PUBLICADO", "2026-10-10T12:30:00Z");

        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto
                        + "/api/v1/comentarios/moderacion/" + mirado + "/contexto?alrededor=2"))
                .header("Authorization", "Bearer " + emisor.tokenDeUsuario("marta", UID_MODERADORA, "MODERADOR"))
                .GET()
                .build();
        HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());

        assertAll(
                () -> assertEquals(200, respuesta.statusCode(), respuesta.body()),
                () -> assertEquals(mirado, JsonPath.read(respuesta.body(), "$.comentarioId")),
                () -> assertEquals(producto, JsonPath.read(respuesta.body(), "$.productoId")),
                () -> assertEquals(List.of(segundo, tercero), JsonPath.read(respuesta.body(), "$.anteriores[*].id")),
                () -> assertEquals(List.of("OCULTO", "PUBLICADO"),
                        JsonPath.read(respuesta.body(), "$.anteriores[*].estado")),
                () -> assertEquals(quinto, JsonPath.read(respuesta.body(), "$.posteriores[0].id")),
                () -> assertEquals("Elena", JsonPath.read(respuesta.body(), "$.posteriores[0].apodoAutor")),
                () -> assertEquals(2, (Integer) JsonPath.read(respuesta.body(), "$.posteriores.length()")),
                () -> assertEquals(7, (Integer) JsonPath.read(respuesta.body(), "$.totalEnElHilo")));

        HttpRequest delPrimero = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto
                        + "/api/v1/comentarios/moderacion/" + primero + "/contexto"))
                .header("Authorization", "Bearer " + emisor.tokenDeUsuario("marta", UID_MODERADORA, "MODERADOR"))
                .GET()
                .build();
        HttpResponse<String> alPrincipio = http.send(delPrimero, HttpResponse.BodyHandlers.ofString());
        assertAll(
                () -> assertEquals(200, alPrincipio.statusCode(), alPrincipio.body()),
                () -> assertEquals(0, (Integer) JsonPath.read(alPrincipio.body(), "$.anteriores.length()")),
                () -> assertEquals(3, (Integer) JsonPath.read(alPrincipio.body(), "$.posteriores.length()")));
    }
}
