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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V21 (1.17.0, 7.4.2): los temas de la base que despliegan las migraciones
 * reciben su título en inglés, en todas sus versiones, y solo donde el tema
 * conserva su título de siempre: lo que un administrador cambió y lo que se
 * creó desde la consola se quedan sin él (lo ponen desde el panel).
 */
@Testcontainers
@DisplayName("V21 · el título en inglés de los temas de la base, sin tocar lo que cambió un administrador")
class TituloEnInglesIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final String EN_PRODUCCION = "SELECT t.clave, t.titulo, t.titulo_en FROM temas_conocimiento t "
        + "JOIN versiones_base_conocimiento v ON v.id = t.version_id WHERE v.estado = 'PRODUCCION' ORDER BY t.clave";

    private Flyway flywayHasta(String version) {
        return Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas("chatbot").defaultSchema("chatbot").createSchemas(true)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion(version))
            .load();
    }

    private Connection conexion() throws SQLException {
        Connection conexion = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
            postgres.getPassword());
        try (Statement sentencia = conexion.createStatement()) {
            sentencia.execute("SET search_path TO chatbot");
        }
        return conexion;
    }

    @Test
    void daElTituloEnInglesALosTemasDeSiempreYRespetaLosCambiados() throws Exception {
        flywayHasta("20").migrate();

        try (Connection c = conexion()) {
            String produccion = uno(c, "SELECT id FROM versiones_base_conocimiento WHERE estado = 'PRODUCCION'");
            // Un administrador le cambio el titulo a un tema de la base.
            ejecutar(c, "UPDATE temas_conocimiento SET titulo = 'Torneos: lo esencial' "
                + "WHERE version_id = ?::uuid AND clave = 'torneo'", produccion);
            // Y otro lo creo desde la consola.
            ejecutar(c, "INSERT INTO temas_conocimiento (id, categoria, tipo_respuesta, titulo, palabras_clave_es, "
                    + "contenido_respuesta_es, activo, version_id, clave, prioridad) VALUES (?, 'FAQ_GENERAL', "
                    + "'DIRECTA', 'Horario del soporte', 'horario soporte', 'De 8 a 18.', TRUE, ?::uuid, "
                    + "'horario-del-soporte', 0)",
                UUID.randomUUID(), produccion);
        }

        flywayHasta("21").migrate();

        try (Connection c = conexion()) {
            Map<String, String> enIngles = new LinkedHashMap<>();
            Set<String> enEspanol = new HashSet<>();
            try (Statement sentencia = c.createStatement(); ResultSet filas = sentencia.executeQuery(EN_PRODUCCION)) {
                while (filas.next()) {
                    enIngles.put(filas.getString(1), filas.getString(3));
                    enEspanol.add(filas.getString(2).toLowerCase());
                }
            }
            // Los 62 de la base y el de la consola.
            assertThat(enIngles).hasSize(63)
                .containsEntry("crear-cuenta", "How to create an account")
                .containsEntry("como-funciona-la-subasta", "How the Auction works")
                .containsEntry("reglas-del-juego", "Basic game rules")
                // Lo que cambio un administrador y lo creado en la consola: sin titulo en ingles.
                .containsEntry("torneo", null)
                .containsEntry("horario-del-soporte", null);
            List<String> titulos = enIngles.values().stream().filter(Objects::nonNull).toList();
            assertThat(titulos).hasSize(61).doesNotHaveDuplicates()
                .allSatisfy(titulo -> assertThat(enEspanol).doesNotContain(titulo.toLowerCase()));

            // Tambien en las versiones anteriores: si se revierte, el ingles sigue ahi.
            assertThat(uno(c, "SELECT count(*) FROM temas_conocimiento t JOIN versiones_base_conocimiento v "
                + "ON v.id = t.version_id WHERE v.estado = 'RETIRADA' AND t.titulo_en IS NOT NULL"))
                .isNotEqualTo("0");

            // El mismo tope que el titulo en espanol.
            assertThatThrownBy(() -> ejecutar(c, "UPDATE temas_conocimiento SET titulo_en = ? "
                + "WHERE clave = 'horario-del-soporte'", "x".repeat(151)))
                .isInstanceOf(SQLException.class);
        }

        assertThat(flywayHasta("21").migrate().migrationsExecuted).isZero();
    }

    private static void ejecutar(Connection c, String sql, Object... valores) throws SQLException {
        try (PreparedStatement sentencia = c.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) {
                sentencia.setObject(i + 1, valores[i]);
            }
            sentencia.executeUpdate();
        }
    }

    private static String uno(Connection c, String sql) throws SQLException {
        List<String> valores = new ArrayList<>();
        try (Statement sentencia = c.createStatement(); ResultSet filas = sentencia.executeQuery(sql)) {
            while (filas.next()) {
                valores.add(filas.getString(1));
            }
        }
        assertThat(valores).hasSize(1);
        return valores.get(0);
    }
}
