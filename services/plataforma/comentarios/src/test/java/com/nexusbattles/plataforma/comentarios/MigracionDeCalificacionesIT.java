package com.nexusbattles.plataforma.comentarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V5 sobre datos de verdad: la migracion lleva las estrellas que ya existian a
 * la tabla de calificaciones sin borrar nada — B3.
 *
 * <p>Se ejecuta Flyway a mano en dos tiempos: hasta V4, se siembran filas con
 * la forma de antes de B3, y despues V5. Es la unica forma de probar una
 * migracion de datos: con la aplicacion arrancada las migraciones ya se
 * habrian aplicado sobre una base vacia.
 */
@Testcontainers
class MigracionDeCalificacionesIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final Instant T1 = Instant.parse("2026-09-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-02T10:00:00Z");

    private Flyway flywayHasta(String version) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("comentarios")
                .defaultSchema("comentarios")
                .locations("classpath:db/migration")
                .target(version)
                .load();
    }

    private Connection conexion() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private void comentario(Connection c, String id, String producto, String autor, Integer estrellas,
            Instant fecha, String estado) throws SQLException {
        try (PreparedStatement insert = c.prepareStatement("""
                INSERT INTO comentarios.comentarios
                    (id, producto_id, autor_id, apodo_autor, texto, estrellas, fecha_publicacion, estado)
                VALUES (?, ?, ?, 'apodo', 'texto', ?, ?, ?)
                """)) {
            insert.setString(1, id);
            insert.setString(2, producto);
            insert.setString(3, autor);
            if (estrellas == null) {
                insert.setNull(4, java.sql.Types.INTEGER);
            } else {
                insert.setInt(4, estrellas);
            }
            insert.setTimestamp(5, Timestamp.from(fecha));
            insert.setString(6, estado);
            insert.executeUpdate();
        }
    }

    @Test
    @DisplayName("una calificacion por producto y autor, de cualquier estado con estrellas, y nada borrado")
    void copiaLasEstrellasExistentes() throws Exception {
        flywayHasta("4").migrate();
        try (Connection c = conexion()) {
            // A en p1: su primer comentario califico (4); el segundo ya fue sin estrellas (D-07).
            comentario(c, "c1", "p1", "autor-a", 4, T1, "PUBLICADO");
            comentario(c, "c2", "p1", "autor-a", null, T2, "PUBLICADO");
            // B en p1: su comentario lo oculto moderacion y conservo las estrellas.
            // Antes contaban en ninguna parte y le impedian volver a calificar.
            comentario(c, "c3", "p1", "autor-b", 2, T1, "OCULTO");
            // C en p1: lo retiro el mismo; HU-COM-004 ya le habia quitado las estrellas.
            comentario(c, "c4", "p1", "autor-c", null, T1, "ELIMINADO");
            // A en p2: en revision, con estrellas.
            comentario(c, "c5", "p2", "autor-a", 5, T2, "EN_REVISION");
        }

        flywayHasta("latest").migrate();

        try (Connection c = conexion()) {
            Map<String, Integer> calificaciones = new LinkedHashMap<>();
            Map<String, Instant> fechas = new LinkedHashMap<>();
            try (ResultSet filas = c.createStatement().executeQuery("""
                    SELECT producto_id, autor_id, estrellas, creada_en, id
                    FROM comentarios.calificaciones ORDER BY producto_id, autor_id
                    """)) {
                while (filas.next()) {
                    String clave = filas.getString(1) + "/" + filas.getString(2);
                    calificaciones.put(clave, filas.getInt(3));
                    fechas.put(clave, filas.getTimestamp(4).toInstant());
                    assertEquals(36, filas.getString(5).length(), "id con forma de UUID");
                }
            }
            assertEquals(Map.of("p1/autor-a", 4, "p1/autor-b", 2, "p2/autor-a", 5), calificaciones);
            assertEquals(T1, fechas.get("p1/autor-a"), "la fecha es la del comentario que califico");

            // Nada se borro: la columna vieja sigue con sus datos.
            try (ResultSet vieja = c.createStatement().executeQuery(
                    "SELECT estrellas FROM comentarios.comentarios WHERE id = 'c3'")) {
                vieja.next();
                assertEquals(2, vieja.getInt(1));
            }
            // Las columnas nuevas nacen en falso para lo que ya existia.
            try (ResultSet marcas = c.createStatement().executeQuery(
                    "SELECT bool_or(editado), bool_or(marcado) FROM comentarios.comentarios")) {
                marcas.next();
                assertFalse(marcas.getBoolean(1));
                assertFalse(marcas.getBoolean(2));
            }
            // Y la restriccion unica esta puesta: la segunda del mismo autor no entra.
            try (PreparedStatement otra = c.prepareStatement("""
                    INSERT INTO comentarios.calificaciones (id, producto_id, autor_id, estrellas, creada_en)
                    VALUES ('x', 'p1', 'autor-a', 1, now()) ON CONFLICT (producto_id, autor_id) DO NOTHING
                    """)) {
                assertEquals(0, otra.executeUpdate());
            }
            try (ResultSet asientos = c.createStatement().executeQuery("""
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema = 'comentarios' AND table_name = 'comentario_moderacion'
                      AND column_name IN ('texto_anterior', 'texto_nuevo', 'ip_origen')
                    """)) {
                asientos.next();
                assertEquals(3, asientos.getInt(1));
            }
            try (ResultSet imagenes = c.createStatement().executeQuery(
                    "SELECT to_regclass('comentarios.imagenes_de_comentario') IS NOT NULL")) {
                imagenes.next();
                assertTrue(imagenes.getBoolean(1), "existe la tabla de imagenes");
            }
            try (ResultSet sinEstrellas = c.createStatement().executeQuery(
                    "SELECT estrellas FROM comentarios.comentarios WHERE id = 'c4'")) {
                sinEstrellas.next();
                assertNull(sinEstrellas.getObject(1), "el retirado por su autor sigue sin estrellas");
            }
        }
    }
}
