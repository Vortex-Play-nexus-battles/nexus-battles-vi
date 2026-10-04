package com.nexusbattles.ms_ecommerce;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V3 y V4 contra PostgreSQL de verdad, con los datos que habia antes.
 *
 * <p>Se migra hasta V2, se siembran dos carritos del mismo jugador (lo que el
 * esquema permitia) con lineas repetidas del mismo producto, y se aplica V3:
 * quedan un carrito por jugador y una linea por producto, con las cantidades
 * sumadas y ninguna linea perdida; y desde ahi la base impide volver a
 * duplicarlos.
 */
@Testcontainers
@DisplayName("Migracion V3/V4: un carrito por jugador sin perder lineas, lista de deseos y ordenes")
class MigracionV3IT {

    @Container
    static final PostgreSQLContainer<?> BASE = new PostgreSQLContainer<>("postgres:15-alpine");

    private static Flyway flywayHasta(String version) {
        return Flyway.configure()
                .dataSource(BASE.getJdbcUrl(), BASE.getUsername(), BASE.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load();
    }

    private static Connection conexion() throws SQLException {
        return DriverManager.getConnection(BASE.getJdbcUrl(), BASE.getUsername(), BASE.getPassword());
    }

    private static long insertar(Connection conexion, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                sentencia.setObject(i + 1, parametros[i]);
            }
            try (ResultSet fila = sentencia.executeQuery()) {
                fila.next();
                return fila.getLong(1);
            }
        }
    }

    private static long numero(Connection conexion, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                sentencia.setObject(i + 1, parametros[i]);
            }
            try (ResultSet fila = sentencia.executeQuery()) {
                fila.next();
                return fila.getLong(1);
            }
        }
    }

    @Test
    @DisplayName("fusiona carritos y lineas repetidas, topa en 20 y deja las restricciones puestas")
    void fusionaYRestringe() throws SQLException {
        flywayHasta("2").migrate();

        long viejo;
        long nuevo;
        long otroJugador;
        try (Connection conexion = conexion()) {
            viejo = insertar(conexion, "INSERT INTO carritos (usuario_id, total) VALUES ('uid-a', 0) RETURNING id");
            nuevo = insertar(conexion, "INSERT INTO carritos (usuario_id, total) VALUES ('uid-a', 0) RETURNING id");
            otroJugador = insertar(conexion, "INSERT INTO carritos (usuario_id, total) VALUES ('uid-b', 0) RETURNING id");
            String linea = "INSERT INTO items_carrito (carrito_id, producto_ref, producto_nombre, moneda, cantidad, "
                    + "precio_unitario, subtotal) VALUES (?, ?, ?, 'COP', ?, ?, ?) RETURNING id";
            insertar(conexion, linea, viejo, "espada", "Espada", 15, 1000, 15000);
            insertar(conexion, linea, nuevo, "espada", "Espada", 9, 1000, 9000);
            insertar(conexion, linea, nuevo, "escudo", "Escudo", 1, 500, 500);
            insertar(conexion, linea, otroJugador, "espada", "Espada", 2, 1000, 2000);
            insertar(conexion, "INSERT INTO items_carrito (carrito_id, cantidad) VALUES (?, 1) RETURNING id", nuevo);
        }

        assertThat(flywayHasta("4").migrate().migrationsExecuted).isEqualTo(2);

        try (Connection conexion = conexion()) {
            assertThat(numero(conexion, "SELECT count(*) FROM carritos WHERE usuario_id = 'uid-a'")).isEqualTo(1);
            assertThat(numero(conexion, "SELECT id FROM carritos WHERE usuario_id = 'uid-a'"))
                    .as("se conserva el mas antiguo").isEqualTo(viejo);
            assertThat(numero(conexion, "SELECT count(*) FROM items_carrito WHERE carrito_id = ?", viejo))
                    .as("espada fundida, escudo y la linea legada").isEqualTo(3);
            assertThat(numero(conexion, "SELECT cantidad FROM items_carrito WHERE carrito_id = ? "
                    + "AND producto_ref = 'espada'", viejo)).as("15 + 9, con el tope de 20").isEqualTo(20);
            assertThat(numero(conexion, "SELECT total FROM carritos WHERE id = ?", viejo)).isEqualTo(20500);
            assertThat(numero(conexion, "SELECT cantidad FROM items_carrito WHERE carrito_id = ?", otroJugador))
                    .isEqualTo(2);

            assertThatThrownBy(() -> insertar(conexion,
                    "INSERT INTO carritos (usuario_id, total) VALUES ('uid-a', 0) RETURNING id"))
                    .as("un segundo carrito del mismo jugador").isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertar(conexion, "INSERT INTO items_carrito (carrito_id, producto_ref, "
                    + "cantidad) VALUES (?, 'escudo', 1) RETURNING id", viejo))
                    .as("una segunda linea del mismo producto").isInstanceOf(SQLException.class);

            insertar(conexion, "INSERT INTO lista_deseos (usuario_id, producto_ref, producto_nombre, agregado_en) "
                    + "VALUES ('uid-a', 'espada', 'Espada', now()) RETURNING id");
            assertThatThrownBy(() -> insertar(conexion, "INSERT INTO lista_deseos (usuario_id, producto_ref) "
                    + "VALUES ('uid-a', 'espada') RETURNING id"))
                    .as("el mismo deseo dos veces").isInstanceOf(SQLException.class);

            assertThat(numero(conexion, "SELECT count(*) FROM information_schema.tables "
                    + "WHERE table_name IN ('ordenes', 'lineas_orden')")).isEqualTo(2);
            assertThat(numero(conexion, "SELECT count(*) FROM information_schema.tables "
                    + "WHERE table_name IN ('productos', 'carritos', 'items_carrito', 'lista_deseos')"))
                    .as("nada se borro").isEqualTo(4);
        }
    }
}
