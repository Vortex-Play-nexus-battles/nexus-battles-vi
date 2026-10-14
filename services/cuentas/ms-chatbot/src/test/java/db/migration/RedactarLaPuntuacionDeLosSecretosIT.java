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
 * V14 (1.11.0, 7.4.8): lo que la redacción anterior guardó con la puntuación
 * final de la contraseña a la vista («***!») queda tapado entero, y lo que no
 * era un secreto no se toca.
 */
@Testcontainers
@DisplayName("V14 · la puntuación de un secreto guardado también se tapa")
class RedactarLaPuntuacionDeLosSecretosIT {

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
    void tapaLoQueQuedabaDelSecretoYNoTocaLoDemas() throws Exception {
        flywayHasta("13").migrate();

        UUID conversacion = UUID.randomUUID();
        UUID ticket = UUID.randomUUID();
        OffsetDateTime ahora = OffsetDateTime.parse("2026-10-14T10:00:00Z");
        try (Connection c = conexion()) {
            ejecutar(c, "INSERT INTO conversaciones VALUES (?, ?, TRUE, ?, ?)", conversacion,
                UUID.randomUUID().toString(), ahora, ahora);
            // Como lo dejaba la redacción de la 1.4.0 a la 1.10.0.
            mensaje(c, conversacion, "USUARIO", "mi contraseña es ***! como me registro", ahora);
            mensaje(c, conversacion, "USUARIO", "¡Hola! ¿Cómo subo de nivel?", ahora.plusSeconds(1));
            mensaje(c, conversacion, "BOT", "¡Claro! Así se sube de nivel.", ahora.plusSeconds(2));
            ejecutar(c, "INSERT INTO tickets_soporte (id, uid, categoria, asunto, mensaje, estado, creado_en, actualizado_en) "
                    + "VALUES (?, 'u-1', 'CUENTA_Y_REGISTRO', ?, ?, 'ABIERTO', ?, ?)",
                ticket, "No puedo entrar", "password: ***?! y nada", ahora, ahora);
            ejecutar(c, "INSERT INTO tickets_soporte_contexto VALUES (?, 0, 'USUARIO', ?, ?)", ticket,
                "mi clave: ***)", ahora);
            ejecutar(c, "INSERT INTO brechas_conocimiento (id, texto_normalizado, ejemplo_pregunta, "
                    + "contador_no_util, contador_escalamiento, fecha_primera_ocurrencia, fecha_ultima_ocurrencia) "
                    + "VALUES (?, 'mi contrasena es no entra', 'mi contraseña es ***! no entra', 0, 2, ?, ?)",
                UUID.randomUUID(), ahora, ahora);
        }

        flywayHasta("14").migrate();

        try (Connection c = conexion()) {
            assertThat(textos(c, "SELECT contenido FROM mensajes ORDER BY fecha_envio")).containsExactly(
                "mi contraseña es *** como me registro",
                "¡Hola! ¿Cómo subo de nivel?",
                "¡Claro! Así se sube de nivel.");
            assertThat(textos(c, "SELECT mensaje FROM tickets_soporte")).containsExactly("password: *** y nada");
            assertThat(textos(c, "SELECT contenido FROM tickets_soporte_contexto")).containsExactly("mi clave: ***");
            assertThat(textos(c, "SELECT ejemplo_pregunta FROM brechas_conocimiento"))
                .containsExactly("mi contraseña es *** no entra");
        }

        assertThat(flywayHasta("14").migrate().migrationsExecuted).isZero();
    }

    private static void mensaje(Connection c, UUID conversacion, String remitente, String contenido,
                                OffsetDateTime fecha) throws Exception {
        ejecutar(c, "INSERT INTO mensajes (id, conversacion_id, remitente, contenido, fecha_envio) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), conversacion, remitente, contenido, fecha);
    }

    private static void ejecutar(Connection c, String sql, Object... valores) throws Exception {
        try (PreparedStatement sentencia = c.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) {
                sentencia.setObject(i + 1, valores[i]);
            }
            sentencia.executeUpdate();
        }
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
