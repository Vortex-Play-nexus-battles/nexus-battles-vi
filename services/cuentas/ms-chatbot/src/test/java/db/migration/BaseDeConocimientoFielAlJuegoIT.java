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
 * V11 (1.5.0): la base de conocimiento se corrige en una versión nueva de
 * producción, sin perder la anterior, también cuando los temas de
 * {@code temas-7-4-2.json} ya se habían importado y cuando hay una candidata
 * en edición.
 */
@Testcontainers
@DisplayName("V11 · la base de conocimiento dice lo que el juego hace")
class BaseDeConocimientoFielAlJuegoIT {

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
    void corrigeEnUnaVersionNuevaLaProduccionYLaCandidata() throws Exception {
        flywayHasta("10").migrate();
        UUID produccion;
        UUID candidata = UUID.randomUUID();
        OffsetDateTime ahora = OffsetDateTime.parse("2026-10-10T10:00:00Z");
        try (Connection c = conexion()) {
            produccion = UUID.fromString(texto(c, "SELECT id::text FROM versiones_base_conocimiento WHERE estado = 'PRODUCCION'"));
            // El tema de logros (#948) ya importado en produccion, con el texto viejo.
            ejecutar(c, "INSERT INTO temas_conocimiento (id, categoria, tipo_respuesta, titulo, palabras_clave_es, "
                    + "contenido_respuesta_es, activo, version_id, clave, prioridad) VALUES (?, 'MECANICA_JUEGO', "
                    + "'DIRECTA', 'Logros, títulos e insignias', 'logros', ?, TRUE, ?, 'logros-titulos-insignias', 0)",
                UUID.randomUUID(), "Puedes revisar tus logros, tu historial de misiones y tu colección de épicas "
                    + "desde Mi Cuenta.", produccion);
            // Una respuesta ya guardada del tema de V2 «Cómo crear una cuenta».
            UUID conversacion = UUID.randomUUID();
            ejecutar(c, "INSERT INTO conversaciones (id, identificador_sesion, autenticado, fecha_inicio, "
                + "fecha_ultima_actividad) VALUES (?, 'anonimo:huella-de-prueba', FALSE, ?, ?)", conversacion, ahora, ahora);
            ejecutar(c, "INSERT INTO mensajes (id, conversacion_id, remitente, contenido, fecha_envio, tema_clave, "
                + "escalado) SELECT gen_random_uuid(), ?, 'BOT', 'Para crear tu cuenta...', ?, clave, FALSE "
                + "FROM temas_conocimiento WHERE version_id = ? AND titulo = 'Cómo crear una cuenta'",
                conversacion, ahora, produccion);
            // Una candidata en edicion, copia de un tema de V2.
            ejecutar(c, "INSERT INTO versiones_base_conocimiento (id, numero, estado, descripcion, fecha_creacion) "
                + "VALUES (?, 2, 'BORRADOR', 'candidata de prueba', ?)", candidata, ahora);
            ejecutar(c, "INSERT INTO temas_conocimiento (id, categoria, tipo_respuesta, titulo, palabras_clave_es, "
                + "contenido_respuesta_es, activo, version_id, clave, prioridad) SELECT gen_random_uuid(), categoria, "
                + "tipo_respuesta, titulo, palabras_clave_es, contenido_respuesta_es, activo, ?, clave, prioridad "
                + "FROM temas_conocimiento WHERE version_id = ? AND titulo = 'Cómo crear una cuenta'",
                candidata, produccion);
        }

        flywayHasta("11").migrate();

        try (Connection c = conexion()) {
            // La anterior queda retirada (se puede revertir) y la nueva responde.
            assertThat(texto(c, "SELECT estado FROM versiones_base_conocimiento WHERE id = '" + produccion + "'"))
                .isEqualTo("RETIRADA");
            String nueva = texto(c, "SELECT id::text FROM versiones_base_conocimiento WHERE estado = 'PRODUCCION'");
            assertThat(nueva).isNotEqualTo(produccion.toString());
            assertThat(texto(c, "SELECT count(*)::text FROM temas_conocimiento WHERE version_id = '" + nueva + "'"))
                .isEqualTo("59");
            assertThat(texto(c, "SELECT count(*)::text FROM temas_conocimiento WHERE version_id = '" + produccion + "'"))
                .as("la version retirada conserva sus temas").isEqualTo("22");

            // #948: ya no promete logros.
            String logros = texto(c, "SELECT contenido_respuesta_es FROM temas_conocimiento WHERE version_id = '"
                + nueva + "' AND clave = 'logros-titulos-insignias'");
            assertThat(logros).contains("no tiene logros").doesNotContain("Puedes revisar tus logros");

            // Los temas de V2 tienen ahora una clave legible y sus casos la siguen.
            assertThat(textos(c, "SELECT clave FROM temas_conocimiento WHERE version_id = '" + nueva + "'"))
                .contains("crear-cuenta", "recuperar-contrasena", "heroes-disponibles", "saludo")
                .allMatch(clave -> !clave.matches("[0-9a-f]{8}-.*"));
            assertThat(textos(c, "SELECT DISTINCT tema_clave_esperada FROM casos_evaluacion "
                + "WHERE tema_clave_esperada IS NOT NULL"))
                .allMatch(clave -> !clave.matches("[0-9a-f]{8}-.*"));
            assertThat(texto(c, "SELECT count(*)::text FROM casos_evaluacion WHERE activo")).isEqualTo(String.valueOf(21 + 118));

            // La respuesta guardada sigue apuntando al mismo tema, con su clave nueva.
            assertThat(texto(c, "SELECT tema_clave FROM mensajes WHERE remitente = 'BOT'")).isEqualTo("crear-cuenta");

            // La candidata recibe las mismas correcciones.
            assertThat(texto(c, "SELECT clave FROM temas_conocimiento WHERE version_id = '" + candidata
                + "' AND titulo = 'Cómo crear una cuenta'")).isEqualTo("crear-cuenta");
            assertThat(texto(c, "SELECT contenido_respuesta_es FROM temas_conocimiento WHERE version_id = '"
                + candidata + "' AND clave = 'crear-cuenta'")).contains("Confirma tu correo");
            assertThat(texto(c, "SELECT count(*)::text FROM temas_conocimiento WHERE version_id = '" + candidata + "'"))
                .isEqualTo("59");
        }
    }

    private static void ejecutar(Connection c, String sql, Object... valores) throws Exception {
        try (PreparedStatement sentencia = c.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) {
                sentencia.setObject(i + 1, valores[i]);
            }
            sentencia.executeUpdate();
        }
    }

    private static String texto(Connection c, String sql) throws Exception {
        List<String> todos = textos(c, sql);
        assertThat(todos).as(sql).hasSize(1);
        return todos.get(0);
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
