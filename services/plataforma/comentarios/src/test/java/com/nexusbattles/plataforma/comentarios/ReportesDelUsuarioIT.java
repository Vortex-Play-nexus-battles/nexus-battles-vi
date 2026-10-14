package com.nexusbattles.plataforma.comentarios;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
import com.nexusbattles.plataforma.comentarios.moderacion.ReportesDelUsuario;

/**
 * Los reportes de una cuenta contra PostgreSQL de verdad — contrato 1.12.0.
 *
 * <p>Lo que los dobles no comprueban: que las consultas derivadas y las dos
 * proyecciones de {@code ComentarioRepository} arrancan contra el esquema real,
 * que «recibidos» sale de los comentarios del autor y «realizados» del
 * reportante, y que la ruta responde con la forma del contrato.
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
@DisplayName("1.12.0: los reportes de una cuenta contra PostgreSQL")
class ReportesDelUsuarioIT {

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

    private static final UUID UID_ADMIN = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @LocalServerPort
    private int puerto;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ReportesDelUsuario reportes;

    private final HttpClient http = HttpClient.newHttpClient();
    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    private String comentario(String autor, String texto) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into comentarios.comentarios
                    (id, producto_id, autor_id, apodo_autor, texto, fecha_publicacion, estado)
                values (?, ?, ?, ?, ?, now(), 'PUBLICADO')
                """, id, "producto-reportes-it", autor, "Apodo " + autor.substring(0, 4), texto);
        return id;
    }

    private void reporte(String comentarioId, String reportante, String cuando) {
        jdbc.update("""
                insert into comentarios.comentario_reportes
                    (id, comentario_id, reportante_id, categoria, descripcion, fecha)
                values (?, ?, ?, 'ACOSO', 'Insulta', cast(? as timestamptz))
                """, UUID.randomUUID().toString(), comentarioId, reportante, cuando);
    }

    @Test
    @DisplayName("realizados salen del reportante y recibidos de los comentarios del autor, del mas reciente al mas antiguo")
    void realizadosYRecibidos() throws Exception {
        String ana = UUID.randomUUID().toString();
        String bruno = UUID.randomUUID().toString();
        String carla = UUID.randomUUID().toString();
        String deAna = comentario(ana, "Un comentario de Ana");
        String deBruno = comentario(bruno, "Un comentario de Bruno");
        reporte(deBruno, ana, "2026-10-01T10:00:00Z");
        reporte(deAna, bruno, "2026-10-02T10:00:00Z");
        reporte(deAna, carla, "2026-10-03T10:00:00Z");

        ReportesDelUsuario.Pagina realizados =
                reportes.consultar(ana, ReportesDelUsuario.Direccion.REALIZADOS, 0, 20);
        ReportesDelUsuario.Pagina recibidos =
                reportes.consultar(ana, ReportesDelUsuario.Direccion.RECIBIDOS, 0, 20);

        assertAll(
                () -> assertEquals(1, realizados.total()),
                () -> assertEquals(bruno, realizados.reportes().get(0).comentario().autorId()),
                () -> assertEquals(2, recibidos.total()),
                () -> assertEquals(1, recibidos.totalRealizados()),
                () -> assertEquals(2, recibidos.totalRecibidos()),
                () -> assertEquals(List.of(deAna, deAna),
                        recibidos.reportes().stream().map(ReportesDelUsuario.Reporte::comentarioId).toList()),
                () -> assertEquals("Un comentario de Ana", recibidos.reportes().get(0).comentario().extracto()),
                () -> assertEquals("2026-10-03T10:00:00Z", recibidos.reportes().get(0).fecha().toString(),
                        "el mas reciente primero"));

        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto
                        + "/api/v1/comentarios/moderacion/usuarios/" + ana + "/reportes?direccion=RECIBIDOS"))
                .header("Authorization", "Bearer " + emisor.tokenDeUsuario("ada", UID_ADMIN, "ADMINISTRADOR"))
                .GET()
                .build();
        HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());

        assertAll(
                () -> assertEquals(200, respuesta.statusCode(), respuesta.body()),
                () -> assertEquals(2, (Integer) JsonPath.read(respuesta.body(), "$.total")),
                () -> assertEquals("RECIBIDOS", JsonPath.read(respuesta.body(), "$.direccion")),
                () -> assertFalse(respuesta.body().contains("reportanteId"), "sin el reportante"));
    }
}
