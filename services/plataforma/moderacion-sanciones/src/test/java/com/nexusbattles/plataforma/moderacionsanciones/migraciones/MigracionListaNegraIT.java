package com.nexusbattles.plataforma.moderacionsanciones.migraciones;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La migracion de una base que ya tenia terminos (V1-V3) a la lista negra
 * normalizada (V4-V7), sin borrar ninguna fila: lo que pasa en DEV al
 * desplegar. Flyway directo, sin Spring, para poder parar en V3 y sembrar
 * filas «de antes».
 */
@Testcontainers
@DisplayName("Migracion · de la lista negra de antes a la normalizada, sin perder filas")
class MigracionListaNegraIT {

    private static final String ESQUEMA = "moderacion_sanciones";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private Flyway flyway(String objetivo) {
        var configuracion = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(ESQUEMA)
                .defaultSchema(ESQUEMA)
                .locations("classpath:db/migration")
                .javaMigrations(new V5__NormalizarTerminosExistentes());
        if (objetivo != null) {
            configuracion.target(objetivo);
        }
        return configuracion.load();
    }

    private Connection conexion() throws SQLException {
        Connection conexion = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
        conexion.setSchema(ESQUEMA);
        return conexion;
    }

    record Fila(String normalizado, String categoria, String modo, boolean activo, String creadoPor,
                boolean conFecha) {
    }

    @Test
    @DisplayName("las filas anteriores quedan con su forma y OTRO; los choques se apagan; la semilla respeta lo que habia")
    void migraSinPerderFilas() throws Exception {
        flyway("3").migrate();
        try (Connection c = conexion(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO terminos_prohibidos (termino, fecha_creacion) VALUES "
                    + "('malapalabra', '2026-09-01 10:00:00'), ('Spider-Man', '2026-09-02 10:00:00'), "
                    + "('spiderman', '2026-09-03 10:00:00'), ('!!!', '2026-09-04 10:00:00'), "
                    + "('culo', '2026-09-05 10:00:00')");
        }

        flyway(null).migrate();

        Map<String, Fila> filas = new LinkedHashMap<>();
        try (Connection c = conexion(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT termino, normalizado, categoria, modo, activo, creado_por, "
                     + "creado_en IS NOT NULL FROM terminos_prohibidos ORDER BY id")) {
            while (r.next()) {
                filas.put(r.getString(1), new Fila(r.getString(2), r.getString(3), r.getString(4), r.getBoolean(5),
                        r.getString(6), r.getBoolean(7)));
            }
        }

        assertThat(filas.get("malapalabra")).isEqualTo(new Fila("malapalabra", "OTRO", "SUBCADENA", true, null, true));
        assertThat(filas.get("Spider-Man")).isEqualTo(new Fila("spiderman", "OTRO", "SUBCADENA", true, null, true));
        assertThat(filas.get("spiderman").normalizado()).isEqualTo("spiderman#3");
        assertThat(filas.get("spiderman").activo()).as("choca con Spider-Man: se conserva apagada").isFalse();
        assertThat(filas.get("!!!")).isEqualTo(new Fila("#4", "OTRO", "PALABRA", false, null, true));
        assertThat(filas.get("culo")).as("la semilla no pisa lo que ya estaba")
                .isEqualTo(new Fila("culo", "OTRO", "PALABRA", true, null, true));
        assertThat(filas.get("batman")).isEqualTo(new Fila("batman", "MARCA", "SUBCADENA", true, "semilla", true));
        assertThat(filas).hasSize(5 + 22 - 2);

        try (Connection c = conexion(); Statement s = c.createStatement()) {
            assertThatThrownBy(() -> s.executeUpdate("INSERT INTO terminos_prohibidos (termino, normalizado, modo) "
                    + "VALUES ('BATMAN!', 'batman', 'SUBCADENA')"))
                    .as("la forma normalizada es unica").isInstanceOf(SQLException.class);
        }
        try (Connection c = conexion(); Statement s = c.createStatement()) {
            assertThatThrownBy(() -> s.executeUpdate("INSERT INTO terminos_prohibidos (termino, normalizado, modo, "
                    + "categoria) VALUES ('nuevo', 'nuevo', 'SUBCADENA', 'DEPORTE')"))
                    .as("solo las categorias del contrato").isInstanceOf(SQLException.class);
        }
    }
}
