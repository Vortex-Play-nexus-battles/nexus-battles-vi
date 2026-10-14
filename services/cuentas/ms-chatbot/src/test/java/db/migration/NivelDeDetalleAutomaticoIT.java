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
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V20 (1.15.0, 7.4.5, D-CHA-13): las preferencias admiten el nivel de detalle
 * AUTOMATICO, y las que ya estaban guardadas no cambian: su nivel lo eligió
 * (o lo guardó) esa persona.
 */
@Testcontainers
@DisplayName("V20 · el nivel de detalle automatico, sin tocar lo ya guardado")
class NivelDeDetalleAutomaticoIT {

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

    private Connection conexion() throws SQLException {
        Connection conexion = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
            postgres.getPassword());
        try (Statement sentencia = conexion.createStatement()) {
            sentencia.execute("SET search_path TO chatbot");
        }
        return conexion;
    }

    private static void guardar(Connection c, String clave, String idioma, String nivel) throws SQLException {
        try (PreparedStatement sentencia = c.prepareStatement("INSERT INTO preferencias_chat "
            + "(clave, idioma, nivel_detalle, actualizado_en) VALUES (?, ?, ?, ?)")) {
            sentencia.setString(1, clave);
            sentencia.setString(2, idioma);
            sentencia.setString(3, nivel);
            sentencia.setObject(4, OffsetDateTime.parse("2026-10-01T12:00:00Z"));
            sentencia.executeUpdate();
        }
    }

    private static Map<String, String> niveles(Connection c) throws SQLException {
        Map<String, String> niveles = new LinkedHashMap<>();
        try (Statement sentencia = c.createStatement();
             ResultSet filas = sentencia.executeQuery(
                 "SELECT clave, idioma || '/' || nivel_detalle FROM preferencias_chat ORDER BY clave")) {
            while (filas.next()) {
                niveles.put(filas.getString(1), filas.getString(2));
            }
        }
        return niveles;
    }

    @Test
    void admiteElAutomaticoYLoGuardadoSigueIgual() throws Exception {
        flywayHasta("19").migrate();
        try (Connection c = conexion()) {
            guardar(c, "a-breve", "ES", "BREVE");
            guardar(c, "b-normal", "AUTOMATICO", "NORMAL");
            guardar(c, "c-detallado", "EN", "DETALLADO");
            // Antes de V20 el automatico no cabia.
            assertThatThrownBy(() -> guardar(c, "d-automatico", "AUTOMATICO", "AUTOMATICO"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("chk_preferencias_chat_nivel_detalle");
        }

        flywayHasta("20").migrate();

        try (Connection c = conexion()) {
            assertThat(niveles(c)).containsExactly(
                Map.entry("a-breve", "ES/BREVE"),
                Map.entry("b-normal", "AUTOMATICO/NORMAL"),
                Map.entry("c-detallado", "EN/DETALLADO"));
            guardar(c, "d-automatico", "AUTOMATICO", "AUTOMATICO");
            assertThat(niveles(c)).containsEntry("d-automatico", "AUTOMATICO/AUTOMATICO");
            // Fuera de los cuatro, sigue sin caber.
            assertThatThrownBy(() -> guardar(c, "e-otro", "ES", "MAXIMO"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("chk_preferencias_chat_nivel_detalle");
        }
    }
}
