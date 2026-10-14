package db.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V16 (1.11.0, 7.4.2 y 7.4.5): la base dice lo que el juego hace hoy. Corrige
 * «Cómo apelar una sanción» (V11, anterior a la apelación sin sesión de SA-4) y
 * los temas creados en DEV que contradecían el juego, solo donde siguen
 * diciendo exactamente lo auditado: lo editado por un administrador no se toca.
 *
 * <p>Un contenedor por prueba: cada una parte de una base distinta.
 */
@Testcontainers
@DisplayName("V16 · la base dice lo que el juego hace hoy, sin pisar lo que un administrador editó")
class BaseDeConocimientoAlDiaIT {

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private Flyway flywayHasta(String version) {
        return Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas("chatbot").defaultSchema("chatbot").createSchemas(true)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion(version))
            .load();
    }

    private Connection conexion() throws Exception {
        Connection conexion = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
            postgres.getPassword());
        try (Statement sentencia = conexion.createStatement()) {
            sentencia.execute("SET search_path TO chatbot");
        }
        return conexion;
    }

    private static JsonNode contenido() throws Exception {
        try (InputStream entrada = BaseDeConocimientoAlDiaIT.class
                .getResourceAsStream(V16__base_de_conocimiento_al_dia.CONTENIDO)) {
            return JsonMapper.builder().build().readTree(entrada);
        }
    }

    private static JsonNode delTitulo(JsonNode contenido, String titulo) {
        for (JsonNode correccion : contenido.path("porTitulo")) {
            if (titulo.equals(correccion.path("titulo").asString())) {
                return correccion;
            }
        }
        throw new AssertionError("No hay correccion para «" + titulo + "»");
    }

    @Test
    void corrigeLoAuditadoEnUnaVersionNuevaYDejaLoEditado() throws Exception {
        flywayHasta("15").migrate();
        JsonNode v16 = contenido();
        JsonNode apelar = v16.path("porClave").get(0);
        JsonNode epicas = delTitulo(v16, "¿Qué épica tiene cada héroe?");
        JsonNode maestro = delTitulo(v16, "¿Quién es el Maestro de Juego?");

        UUID candidata = UUID.randomUUID();
        int temasAntes;
        try (Connection c = conexion()) {
            String produccion = uno(c, "SELECT id FROM versiones_base_conocimiento WHERE estado = 'PRODUCCION'");
            // Como en DEV: un tema creado desde la consola que dice lo auditado...
            tema(c, produccion, "dev-epicas", epicas.path("titulo").asString(), epicas.path("antesEs").asString(),
                "Each one has an effect for every hero.");
            // ...y otro con un titulo auditado que un administrador ya edito.
            tema(c, produccion, "dev-maestro", maestro.path("titulo").asString(), "Editado por un administrador.",
                null);
            // Una intencion adicional en el tema de V11: la copia la conserva.
            ejecutar(c, "INSERT INTO intenciones_adicionales_de_tema (tema_id, categoria) SELECT id, 'FAQ_GENERAL' "
                + "FROM temas_conocimiento WHERE version_id = ?::uuid AND clave = 'apelar-sancion'", produccion);
            // Una candidata en edicion: el tema de V11 tal cual y uno de DEV ya editado.
            ejecutar(c, "INSERT INTO versiones_base_conocimiento (id, numero, estado, descripcion, fecha_creacion) "
                + "VALUES (?, (SELECT MAX(numero) + 1 FROM versiones_base_conocimiento), 'BORRADOR', 'en edicion', ?)",
                candidata, OffsetDateTime.parse("2026-10-14T09:00:00Z"));
            tema(c, candidata.toString(), "apelar-sancion", "Cómo apelar una sanción", apelar.path("antesEs").asString(),
                "Old english.");
            tema(c, candidata.toString(), "dev-epicas", epicas.path("titulo").asString(), "Editada en la candidata.",
                null);
            temasAntes = Integer.parseInt(uno(c, "SELECT count(*) FROM temas_conocimiento t JOIN "
                + "versiones_base_conocimiento v ON v.id = t.version_id WHERE v.estado = 'PRODUCCION'"));
        }

        flywayHasta("16").migrate();

        try (Connection c = conexion()) {
            assertThat(textos(c, "SELECT descripcion FROM versiones_base_conocimiento WHERE estado = 'PRODUCCION'"))
                .singleElement().asString().contains("V16");
            // La de V15 queda retirada, con sus temas: se puede revertir.
            assertThat(uno(c, "SELECT count(*) FROM versiones_base_conocimiento WHERE estado = 'RETIRADA' "
                + "AND descripcion LIKE '%V15%'")).isEqualTo("1");
            String enProduccion = "SELECT t.%s FROM temas_conocimiento t JOIN versiones_base_conocimiento v "
                + "ON v.id = t.version_id WHERE v.estado = 'PRODUCCION' AND t.clave = '%s'";
            assertThat(uno(c, "SELECT count(*) FROM temas_conocimiento t JOIN versiones_base_conocimiento v "
                + "ON v.id = t.version_id WHERE v.estado = 'PRODUCCION'")).isEqualTo(String.valueOf(temasAntes));

            // El tema de V11: la apelacion sin sesion, en los dos idiomas.
            assertThat(uno(c, String.format(enProduccion, "contenido_respuesta_es", "apelar-sancion")))
                .isEqualTo(apelar.path("respuestaEs").asString())
                .contains("«Apelar esta sanción»", "15 días hábiles")
                .doesNotContain("no se pueden apelar");
            assertThat(uno(c, String.format(enProduccion, "contenido_respuesta_en", "apelar-sancion")))
                .contains("«Apelar esta sanción» (Appeal this sanction)");
            assertThat(uno(c, String.format(enProduccion, "palabras_clave_es", "apelar-sancion")))
                .startsWith("apelar, apelacion,").contains("apelar sin iniciar sesion");
            assertThat(textos(c, "SELECT i.categoria FROM intenciones_adicionales_de_tema i JOIN temas_conocimiento t "
                + "ON t.id = i.tema_id JOIN versiones_base_conocimiento v ON v.id = t.version_id "
                + "WHERE v.estado = 'PRODUCCION' AND t.clave = 'apelar-sancion'")).containsExactly("FAQ_GENERAL");

            // El de DEV que decia lo auditado, corregido en los dos idiomas; el editado, igual.
            assertThat(uno(c, String.format(enProduccion, "contenido_respuesta_es", "dev-epicas")))
                .isEqualTo(epicas.path("respuestaEs").asString())
                .contains("no tienen efecto general");
            assertThat(uno(c, String.format(enProduccion, "contenido_respuesta_en", "dev-epicas")))
                .isEqualTo(epicas.path("respuestaEn").asString());
            assertThat(uno(c, String.format(enProduccion, "contenido_respuesta_es", "dev-maestro")))
                .isEqualTo("Editado por un administrador.");

            // La candidata, con la misma regla.
            String enLaCandidata = "SELECT contenido_respuesta_es FROM temas_conocimiento WHERE version_id = '"
                + candidata + "' AND clave = '%s'";
            assertThat(uno(c, String.format(enLaCandidata, "apelar-sancion")))
                .isEqualTo(apelar.path("respuestaEs").asString());
            assertThat(uno(c, String.format(enLaCandidata, "dev-epicas"))).isEqualTo("Editada en la candidata.");

            assertThat(textos(c, "SELECT tema_clave_esperada FROM casos_evaluacion WHERE pregunta IN "
                + "('¿Puedo apelar si me banearon?', 'Can I appeal a ban without signing in?')"))
                .containsExactly("apelar-sancion", "apelar-sancion");
        }

        assertThat(flywayHasta("16").migrate().migrationsExecuted).isZero();
    }

    @Test
    void sinNadaQueCorregirNoPublicaUnaVersionNueva() throws Exception {
        flywayHasta("15").migrate();
        String produccion;
        try (Connection c = conexion()) {
            produccion = uno(c, "SELECT id FROM versiones_base_conocimiento WHERE estado = 'PRODUCCION'");
            // Un administrador ya reescribio el tema de V11 desde el panel.
            ejecutar(c, "UPDATE temas_conocimiento SET contenido_respuesta_es = 'Reescrito desde el panel.' "
                + "WHERE version_id = ?::uuid AND clave = 'apelar-sancion'", produccion);
        }

        flywayHasta("16").migrate();

        try (Connection c = conexion()) {
            assertThat(uno(c, "SELECT id FROM versiones_base_conocimiento WHERE estado = 'PRODUCCION'"))
                .isEqualTo(produccion);
            assertThat(uno(c, "SELECT count(*) FROM versiones_base_conocimiento WHERE descripcion LIKE '%V16%'"))
                .isEqualTo("0");
            assertThat(uno(c, "SELECT contenido_respuesta_es FROM temas_conocimiento WHERE version_id = '"
                + produccion + "' AND clave = 'apelar-sancion'")).isEqualTo("Reescrito desde el panel.");
            // Los casos de evaluacion si se suman: no dependen de la version.
            assertThat(uno(c, "SELECT count(*) FROM casos_evaluacion WHERE pregunta = '¿Puedo apelar si me banearon?'"))
                .isEqualTo("1");
        }
    }

    private static void tema(Connection c, String version, String clave, String titulo, String respuestaEs,
                             String respuestaEn) throws Exception {
        ejecutar(c, "INSERT INTO temas_conocimiento (id, categoria, tipo_respuesta, titulo, palabras_clave_es, "
                + "palabras_clave_en, contenido_respuesta_es, contenido_respuesta_en, activo, version_id, clave, "
                + "prioridad) VALUES (?, 'FAQ_GENERAL', 'DIRECTA', ?, ?, NULL, ?, ?, TRUE, ?::uuid, ?, 0)",
            UUID.randomUUID(), titulo, clave.replace('-', ' '), respuestaEs, respuestaEn, version, clave);
    }

    private static void ejecutar(Connection c, String sql, Object... valores) throws Exception {
        try (PreparedStatement sentencia = c.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) {
                sentencia.setObject(i + 1, valores[i]);
            }
            sentencia.executeUpdate();
        }
    }

    private static String uno(Connection c, String sql) throws Exception {
        List<String> valores = textos(c, sql);
        assertThat(valores).hasSize(1);
        return valores.get(0);
    }

    private static List<String> textos(Connection c, String sql) throws Exception {
        List<String> textos = new ArrayList<>();
        try (Statement sentencia = c.createStatement(); ResultSet filas = sentencia.executeQuery(sql)) {
            while (filas.next()) {
                textos.add(filas.getString(1));
            }
        }
        return textos;
    }
}
