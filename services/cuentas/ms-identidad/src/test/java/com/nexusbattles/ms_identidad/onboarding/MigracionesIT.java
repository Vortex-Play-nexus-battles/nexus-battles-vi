package com.nexusbattles.ms_identidad.onboarding;

import com.nexusbattles.ms_identidad.MsIdentidadApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El paso de ms-identidad a Flyway (R17, regla 8) contra PostgreSQL real, por
 * los dos caminos que existen de verdad:
 * <ul>
 *   <li><b>Base vacia</b> (banco E2E, un entorno nuevo): V1 crea el esquema,
 *       V2 las tablas del alta, y Hibernate en {@code validate} confirma que
 *       las entidades casan con lo que crearon las migraciones.</li>
 *   <li><b>Base heredada</b> (DEV en AWS): tablas creadas por Hibernate con
 *       {@code ddl-auto}, con datos y sin historial de Flyway. Se marca como
 *       linea base V1 sin tocarla, se aplica solo V2 y los datos siguen ahi.
 *       Incluye el caso de una base heredada en la que Hibernate no llego a
 *       poner la unicidad de {@code public_id}.</li>
 * </ul>
 * Cada caso en su propia base dentro del mismo contenedor. El servicio se
 * arranca con la configuracion de despliegue ({@code validate},
 * baseline-on-migrate), sin servidor web.
 */
@Testcontainers
@DisplayName("Migraciones de ms-identidad: base nueva y base heredada (R17, regla 8)")
class MigracionesIT {

    @Container
    static final PostgreSQLContainer<?> BASE = new PostgreSQLContainer<>("postgres:15-alpine");

    private static String crearBase(String nombre) throws Exception {
        try (Connection conexion = DriverManager.getConnection(BASE.getJdbcUrl(), BASE.getUsername(), BASE.getPassword());
             Statement sentencia = conexion.createStatement()) {
            sentencia.execute("CREATE DATABASE " + nombre);
        }
        return BASE.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + nombre + "$1");
    }

    /** Arranca ms-identidad sobre {@code url}; los argumentos mandan sobre cualquier properties. */
    private static ConfigurableApplicationContext arrancar(String url, String... extra) {
        List<String> argumentos = new ArrayList<>(List.of(
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + BASE.getUsername(),
                "--spring.datasource.password=" + BASE.getPassword(),
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                "--spring.main.web-application-type=none",
                "--app.onboarding.ejecucion=manual",
                "--app.onboarding.reintentos-automaticos=false"));
        argumentos.addAll(List.of(extra));
        return new SpringApplicationBuilder(MsIdentidadApplication.class)
                .profiles("test")
                .run(argumentos.toArray(String[]::new));
    }

    /** Como arranca en DEV desde R17: Flyway con linea base y Hibernate solo validando. */
    private static ConfigurableApplicationContext arrancarComoDespliegue(String url) {
        return arrancar(url,
                "--spring.flyway.enabled=true",
                "--spring.flyway.baseline-on-migrate=true",
                "--spring.flyway.baseline-version=1",
                "--spring.jpa.hibernate.ddl-auto=validate");
    }

    /** Como estaba DEV antes de R17: sin Flyway, Hibernate creando las tablas. */
    private static UUID baseHeredadaConUnJugador(String url) {
        UUID uid = UUID.randomUUID();
        try (ConfigurableApplicationContext heredado = arrancar(url,
                "--spring.flyway.enabled=false",
                "--spring.jpa.hibernate.ddl-auto=create")) {
            JdbcTemplate jdbc = heredado.getBean(JdbcTemplate.class);
            Long rol = jdbc.queryForObject("SELECT id FROM roles WHERE nombre = 'JUGADOR'", Long.class);
            jdbc.update("INSERT INTO usuarios (public_id, apodo, email, password, estado, rol_id, intentos_fallidos,"
                            + " version_token, creado_en) VALUES (?, 'veterana', 'veterana@upb.edu.co', 'x', 'ACTIVO', ?,"
                            + " 0, 0, ?)",
                    uid, rol, Timestamp.valueOf(LocalDateTime.now()));
            // Hibernate de HOY conoce las entidades del alta y las acaba de
            // crear; la base de DEV no las tiene, porque el codigo que la creo
            // no existia. Se quitan para que la base sea la de verdad.
            jdbc.execute("DROP TABLE onboarding_paso");
            jdbc.execute("DROP TABLE onboarding_jugador");
        }
        return uid;
    }

    private static List<Map<String, Object>> historial(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT version, type, success FROM flyway_schema_history ORDER BY installed_rank");
    }

    @Test
    @DisplayName("base vacia: V1 y V2 se aplican y las entidades validan contra ellas")
    void baseVacia() throws Exception {
        String url = crearBase("nueva");
        try (ConfigurableApplicationContext contexto = arrancarComoDespliegue(url)) {
            JdbcTemplate jdbc = contexto.getBean(JdbcTemplate.class);

            assertThat(historial(jdbc)).extracting(fila -> fila.get("version"), fila -> fila.get("type"))
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("1", "SQL"),
                            org.assertj.core.groups.Tuple.tuple("2", "SQL"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM roles", Integer.class))
                    .as("el sembrador de roles funciona sobre el esquema de Flyway").isEqualTo(4);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name IN ('onboarding_jugador','onboarding_paso')",
                    Integer.class)).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("base heredada con datos: se marca como V1 sin tocarla, se aplica V2 y el jugador sigue ahi")
    void baseHeredada() throws Exception {
        String url = crearBase("heredada");
        UUID veterana = baseHeredadaConUnJugador(url);

        try (ConfigurableApplicationContext contexto = arrancarComoDespliegue(url)) {
            JdbcTemplate jdbc = contexto.getBean(JdbcTemplate.class);

            assertThat(historial(jdbc)).extracting(fila -> fila.get("version"), fila -> fila.get("type"))
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("1", "BASELINE"),
                            org.assertj.core.groups.Tuple.tuple("2", "SQL"));
            assertThat(jdbc.queryForObject("SELECT apodo FROM usuarios WHERE public_id = ?", String.class, veterana))
                    .isEqualTo("veterana");

            // La clave ajena del alta funciona sobre la columna heredada...
            jdbc.update("INSERT INTO onboarding_jugador (usuario_uid, version_bootstrap, estado, intentos, creado_en,"
                    + " actualizado_en) VALUES (?, 1, 'PENDIENTE', 0, now(), now())", veterana);
            // ...y no deja crear el alta de un jugador que no existe.
            assertThatThrownBy(() -> jdbc.update("INSERT INTO onboarding_jugador (usuario_uid, version_bootstrap,"
                    + " estado, intentos, creado_en, actualizado_en) VALUES (?, 1, 'PENDIENTE', 0, now(), now())",
                    UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
            // Y el CHECK de estados rechaza lo que no es un estado del alta.
            assertThatThrownBy(() -> jdbc.update("UPDATE onboarding_jugador SET estado = 'INVENTADO'"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    @DisplayName("base heredada sin unicidad en public_id: V2 la pone antes de la clave ajena")
    void baseHeredadaSinUnicidad() throws Exception {
        String url = crearBase("sinunicidad");
        baseHeredadaConUnJugador(url);
        try (Connection conexion = DriverManager.getConnection(url, BASE.getUsername(), BASE.getPassword());
             Statement sentencia = conexion.createStatement()) {
            sentencia.execute("""
                    DO $$
                    DECLARE r record;
                    BEGIN
                      FOR r IN SELECT con.conname FROM pg_constraint con
                               JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = con.conkey[1]
                               WHERE con.conrelid = 'usuarios'::regclass AND con.contype = 'u'
                                 AND a.attname = 'public_id'
                      LOOP
                        EXECUTE 'ALTER TABLE usuarios DROP CONSTRAINT ' || quote_ident(r.conname);
                      END LOOP;
                    END $$;
                    """);
        }

        try (ConfigurableApplicationContext contexto = arrancarComoDespliegue(url)) {
            JdbcTemplate jdbc = contexto.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE conname = 'uk_usuarios_public_id'", Integer.class))
                    .isEqualTo(1);
            assertThat(historial(jdbc)).hasSize(2);
        }
    }
}
