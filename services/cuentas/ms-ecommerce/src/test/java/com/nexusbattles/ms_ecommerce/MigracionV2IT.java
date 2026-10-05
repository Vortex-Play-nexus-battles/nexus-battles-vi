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

/**
 * V2 contra PostgreSQL de verdad, con datos legados de por medio.
 *
 * <p>Se migra hasta V1, se siembran lineas del carrito tal como las dejaba el
 * esquema anterior (apuntando a la tabla local {@code productos}) y se aplica
 * V2. Lo que se afirma es lo que promete la migracion: rellena el nombre y la
 * moneda de las lineas legadas, no inventa referencias al catalogo maestro y
 * no borra ninguna tabla, columna ni fila.
 */
@Testcontainers
@DisplayName("Migracion V2: el carrito pasa al catalogo maestro sin borrar nada")
class MigracionV2IT {

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

    private static String texto(Connection conexion, String sql, long id) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, id);
            try (ResultSet fila = sentencia.executeQuery()) {
                fila.next();
                return fila.getString(1);
            }
        }
    }

    private static long contar(Connection conexion, String sql) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
             ResultSet fila = sentencia.executeQuery()) {
            fila.next();
            return fila.getLong(1);
        }
    }

    @Test
    @DisplayName("rellena nombre y moneda de las lineas legadas, sin referencia inventada y sin borrar nada")
    void migraLasLineasLegadas() throws SQLException {
        flywayHasta("1").migrate();

        long productoLocal;
        long conProducto;
        long sinPrecio;
        try (Connection conexion = conexion()) {
            productoLocal = insertar(conexion, "INSERT INTO productos (nombre, precio_base_cop, tipo, en_promocion, "
                    + "porcentaje_descuento) VALUES ('Espada Larga', 25000.00, 'ARMA', false, 0) RETURNING id");
            long carrito = insertar(conexion,
                    "INSERT INTO carritos (usuario_id, total) VALUES ('uid-legado', 25000.00) RETURNING id");
            conProducto = insertar(conexion, "INSERT INTO items_carrito (carrito_id, producto_id, cantidad, "
                    + "precio_unitario, subtotal) VALUES (?, ?, 1, 25000.00, 25000.00) RETURNING id", carrito, productoLocal);
            sinPrecio = insertar(conexion, "INSERT INTO items_carrito (carrito_id, producto_id, cantidad, "
                    + "precio_unitario, subtotal) VALUES (?, NULL, 1, NULL, NULL) RETURNING id", carrito);
        }

        assertThat(flywayHasta("2").migrate().migrationsExecuted).isEqualTo(1);

        try (Connection conexion = conexion()) {
            assertThat(texto(conexion, "SELECT producto_nombre FROM items_carrito WHERE id = ?", conProducto))
                    .isEqualTo("Espada Larga");
            assertThat(texto(conexion, "SELECT moneda FROM items_carrito WHERE id = ?", conProducto))
                    .isEqualTo("COP");
            assertThat(texto(conexion, "SELECT producto_ref FROM items_carrito WHERE id = ?", conProducto))
                    .as("un id de la tabla local no es una referencia del catalogo maestro")
                    .isNull();
            assertThat(texto(conexion, "SELECT producto_id::text FROM items_carrito WHERE id = ?", conProducto))
                    .as("el vinculo legado se conserva")
                    .isEqualTo(Long.toString(productoLocal));

            assertThat(texto(conexion, "SELECT producto_nombre FROM items_carrito WHERE id = ?", sinPrecio)).isNull();
            assertThat(texto(conexion, "SELECT moneda FROM items_carrito WHERE id = ?", sinPrecio)).isNull();

            assertThat(contar(conexion, "SELECT count(*) FROM productos")).isEqualTo(1);
            assertThat(contar(conexion, "SELECT count(*) FROM items_carrito")).isEqualTo(2);
            assertThat(contar(conexion, "SELECT count(*) FROM information_schema.tables "
                    + "WHERE table_name IN ('productos', 'carritos', 'items_carrito', 'lista_deseos')")).isEqualTo(4);
            assertThat(contar(conexion, "SELECT count(*) FROM information_schema.columns WHERE table_name = "
                    + "'items_carrito' AND column_name IN ('producto_id', 'producto_ref', 'producto_nombre', 'moneda')"))
                    .isEqualTo(4);
            assertThat(contar(conexion, "SELECT character_maximum_length FROM information_schema.columns "
                    + "WHERE table_name = 'items_carrito' AND column_name = 'producto_ref'")).isEqualTo(64);
            assertThat(contar(conexion, "SELECT character_maximum_length FROM information_schema.columns "
                    + "WHERE table_name = 'items_carrito' AND column_name = 'moneda'")).isEqualTo(3);
        }
    }
}
