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
 * V10 (1.4.0, 7.4.8): lo que se guardó sin redactar antes de la 1.4.0 queda
 * redactado al migrar, y dos brechas que solo se distinguían por el secreto se
 * funden en una con la suma de sus contadores.
 */
@Testcontainers
@DisplayName("V10 · redacta lo que ya estaba guardado")
class RedactarDatosSensiblesGuardadosIT {

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
    void redactaMensajesComentariosTicketsYBrechas() throws Exception {
        flywayHasta("9").migrate();

        UUID conversacion = UUID.randomUUID();
        UUID pregunta = UUID.randomUUID();
        UUID respuesta = UUID.randomUUID();
        UUID ticket = UUID.randomUUID();
        OffsetDateTime ahora = OffsetDateTime.parse("2026-10-01T10:00:00Z");
        try (Connection c = conexion()) {
            ejecutar(c, "INSERT INTO conversaciones VALUES (?, ?, TRUE, ?, ?)", conversacion,
                UUID.randomUUID().toString(), ahora, ahora);
            ejecutar(c, "INSERT INTO mensajes (id, conversacion_id, remitente, contenido, fecha_envio) VALUES (?, ?, 'USUARIO', ?, ?)",
                pregunta, conversacion, "mi contraseña es Hunter2!x y la tarjeta 4111 1111 1111 1111", ahora);
            ejecutar(c, "INSERT INTO mensajes (id, conversacion_id, remitente, contenido, fecha_envio) VALUES (?, ?, 'BOT', ?, ?)",
                respuesta, conversacion, "Te ayudo con tu cuenta.", ahora);
            ejecutar(c, "INSERT INTO calificaciones VALUES (?, ?, FALSE, ?, ?)", UUID.randomUUID(), respuesta,
                "token=ghp_abcdefghijklmnopqrstuvwxyz0123456789", ahora);
            ejecutar(c, "INSERT INTO tickets_soporte (id, uid, categoria, asunto, mensaje, estado, creado_en, actualizado_en) "
                    + "VALUES (?, 'u-1', 'CUENTA_Y_REGISTRO', ?, ?, 'ABIERTO', ?, ?)",
                ticket, "mi pin: 4321", "no entro, password: Hunter2!x", ahora, ahora);
            ejecutar(c, "INSERT INTO tickets_soporte_contexto VALUES (?, 0, 'USUARIO', ?, ?)", ticket,
                "mi contraseña es Hunter2!x", ahora);
            // Dos brechas que solo se distinguen por el secreto, y una que solo era el secreto.
            brecha(c, "mi contrasena es abc123x", "Mi contraseña es abc123X", 1, 2, ahora.minusDays(3),
                ahora.minusDays(2));
            brecha(c, "mi contrasena es zzz999q", "mi contraseña es zzz999Q", 4, 0, ahora.minusDays(1), ahora);
            brecha(c, "mi contrasena es", "Mi contraseña es", 0, 1, ahora.minusDays(5), ahora.minusDays(4));
            brecha(c, "token ghp abcdefghijklmnopqrstuvwxyz0123456789",
                "ghp_abcdefghijklmnopqrstuvwxyz0123456789", 0, 1, ahora, ahora);
            brecha(c, "como subo de nivel", "¿Cómo subo de nivel?", 2, 3, ahora, ahora);
        }

        flywayHasta("10").migrate();

        try (Connection c = conexion()) {
            String todo = String.join("\n", textos(c,
                "SELECT contenido FROM mensajes UNION ALL SELECT comentario FROM calificaciones "
                    + "UNION ALL SELECT asunto FROM tickets_soporte UNION ALL SELECT mensaje FROM tickets_soporte "
                    + "UNION ALL SELECT contenido FROM tickets_soporte_contexto "
                    + "UNION ALL SELECT ejemplo_pregunta FROM brechas_conocimiento "
                    + "UNION ALL SELECT texto_normalizado FROM brechas_conocimiento")).toLowerCase();
            assertThat(todo).doesNotContain("hunter2").doesNotContain("4111 1111").doesNotContain("4321")
                .doesNotContain("ghp_").doesNotContain("abc123").doesNotContain("zzz999");
            assertThat(textos(c, "SELECT contenido FROM mensajes WHERE remitente = 'BOT'"))
                .containsExactly("Te ayudo con tu cuenta.");

            // Las tres de «mi contraseña es» quedan en una sola, con todo sumado;
            // la que solo era un token desaparece.
            List<String> claves = textos(c, "SELECT texto_normalizado || '|' || contador_no_util || '|' "
                + "|| contador_escalamiento FROM brechas_conocimiento ORDER BY texto_normalizado");
            assertThat(claves).containsExactly("como subo de nivel|2|3", "mi contrasena es|5|3");
            assertThat(textos(c, "SELECT to_char(fecha_primera_ocurrencia AT TIME ZONE 'UTC', 'YYYY-MM-DD') || ' ' "
                + "|| to_char(fecha_ultima_ocurrencia AT TIME ZONE 'UTC', 'YYYY-MM-DD') FROM brechas_conocimiento "
                + "WHERE texto_normalizado = 'mi contrasena es'")).containsExactly("2026-09-26 2026-10-01");
        }

        // Volver a migrar no cambia nada (ya aplicada) y la migracion es idempotente.
        assertThat(flywayHasta("10").migrate().migrationsExecuted).isZero();
    }

    private static void brecha(Connection c, String normalizado, String ejemplo, int noUtil, int escalamiento,
                               OffsetDateTime primera, OffsetDateTime ultima) throws Exception {
        ejecutar(c, "INSERT INTO brechas_conocimiento VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), normalizado,
            ejemplo, noUtil, escalamiento, primera, ultima);
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
