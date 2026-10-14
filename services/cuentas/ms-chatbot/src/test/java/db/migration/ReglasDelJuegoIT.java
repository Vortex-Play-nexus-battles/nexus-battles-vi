package db.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

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
 * V15 (1.11.0, 7.4.2): «Reglas básicas del juego» llega a producción como
 * versión nueva (la anterior queda RETIRADA y se puede revertir), con las
 * intenciones adicionales de los demás temas copiadas; la candidata en
 * edición también lo recibe, y se añaden sus casos de evaluación.
 */
@Testcontainers
@DisplayName("V15 · el tema de las reglas del juego, sin perder la historia")
class ReglasDelJuegoIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

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

    @Test
    void publicaElTemaEnUnaVersionNuevaYLoDaTambienALaCandidata() throws Exception {
        flywayHasta("14").migrate();

        UUID candidata = UUID.randomUUID();
        try (Connection c = conexion()) {
            String produccion = uno(c, "SELECT id FROM versiones_base_conocimiento WHERE estado = 'PRODUCCION'");
            // Una intencion adicional en un tema de produccion: la copia la conserva.
            ejecutar(c, "INSERT INTO intenciones_adicionales_de_tema (tema_id, categoria) "
                + "SELECT id, 'FAQ_GENERAL' FROM temas_conocimiento WHERE version_id = ?::uuid AND clave = 'torneo'",
                produccion);
            // Una candidata en edicion, con un tema suyo.
            ejecutar(c, "INSERT INTO versiones_base_conocimiento (id, numero, estado, descripcion, fecha_creacion) "
                + "VALUES (?, (SELECT MAX(numero) + 1 FROM versiones_base_conocimiento), 'BORRADOR', 'en edicion', ?)",
                candidata, OffsetDateTime.parse("2026-10-14T09:00:00Z"));
            ejecutar(c, "INSERT INTO temas_conocimiento (id, categoria, tipo_respuesta, titulo, palabras_clave_es, "
                    + "contenido_respuesta_es, activo, version_id, clave, prioridad) VALUES (?, 'FAQ_GENERAL', "
                    + "'DIRECTA', 'Un tema de la candidata', 'tema candidata', 'Respuesta.', TRUE, ?, 'tema-candidata', 0)",
                UUID.randomUUID(), candidata);
        }
        int temasAntes;
        try (Connection c = conexion()) {
            temasAntes = Integer.parseInt(uno(c, "SELECT count(*) FROM temas_conocimiento t JOIN "
                + "versiones_base_conocimiento v ON v.id = t.version_id WHERE v.estado = 'PRODUCCION'"));
        }

        flywayHasta("15").migrate();

        try (Connection c = conexion()) {
            assertThat(textos(c, "SELECT descripcion FROM versiones_base_conocimiento WHERE estado = 'PRODUCCION'"))
                .singleElement().asString().contains("V15");
            // La anterior queda retirada, con sus temas: se puede revertir.
            assertThat(uno(c, "SELECT count(*) FROM versiones_base_conocimiento WHERE estado = 'RETIRADA' "
                + "AND descripcion LIKE '%V11%'")).isEqualTo("1");
            String enProduccion = "SELECT t.%s FROM temas_conocimiento t JOIN versiones_base_conocimiento v "
                + "ON v.id = t.version_id WHERE v.estado = 'PRODUCCION'";
            assertThat(textos(c, String.format(enProduccion, "clave"))).hasSize(temasAntes + 1)
                .contains("reglas-del-juego", "combate-por-turnos", "torneo");
            assertThat(textos(c, String.format(enProduccion, "palabras_clave_es") + " AND t.clave = 'reglas-del-juego'"))
                .singleElement().asString().contains("reglas del juego, cuales son las reglas");
            assertThat(textos(c, "SELECT i.categoria FROM intenciones_adicionales_de_tema i JOIN temas_conocimiento t "
                + "ON t.id = i.tema_id JOIN versiones_base_conocimiento v ON v.id = t.version_id "
                + "WHERE v.estado = 'PRODUCCION' AND t.clave = 'torneo'")).containsExactly("FAQ_GENERAL");
            // La candidata tambien lo tiene, y conserva lo suyo.
            assertThat(textos(c, "SELECT clave FROM temas_conocimiento WHERE version_id = '" + candidata + "'"))
                .containsExactlyInAnyOrder("tema-candidata", "reglas-del-juego");
            assertThat(textos(c, "SELECT tema_clave_esperada FROM casos_evaluacion WHERE pregunta IN "
                + "('¿Cuáles son las reglas del juego?', 'What are the game rules?')"))
                .containsExactly("reglas-del-juego", "reglas-del-juego");
        }

        assertThat(flywayHasta("15").migrate().migrationsExecuted).isZero();
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
