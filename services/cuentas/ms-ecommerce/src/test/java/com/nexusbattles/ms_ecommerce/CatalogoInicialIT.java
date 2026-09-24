package com.nexusbattles.ms_ecommerce;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * La migracion V2 deja la tienda con el catalogo inicial, y no lo duplica.
 *
 * <p><b>Por que existe.</b> La vitrina ({@code GET /api/v1/productos}) lee la
 * tabla {@code productos}, que en el servidor estaba vacia: la tienda mostraba
 * "La tienda no tiene productos ahora mismo". El {@code data.sql} del servicio
 * nunca se ejecuta (el esquema va por Flyway y la base no es embebida), asi
 * que el catalogo entra por una migracion: {@code V2__catalogo_inicial.sql},
 * con los 56 productos de las reglas del curso (8 heroes, 16 armas, 16
 * armaduras, 8 items y 8 habilidades epicas).
 *
 * <p><b>Que fija.</b> Que tras migrar hay exactamente esos 56, con el precio de
 * demostracion de su tipo; que volver a correr el INSERT no agrega nada (es
 * idempotente por nombre y tipo); y que una base creada antes de Flyway —la
 * que se baselinea en la version 1, como la del servidor— tambien lo recibe.
 *
 * <p>Sin contexto de Spring: lo que se prueba es la migracion contra el
 * PostgreSQL de verdad, no el arranque (eso ya lo cubre
 * {@link ArranqueDeLaAplicacionIT}).
 */
@Testcontainers
@DisplayName("La migracion V2 carga el catalogo inicial de la tienda")
class CatalogoInicialIT {

    private static final String MIGRACION_V2 = "db/migration/V2__catalogo_inicial.sql";
    private static final String MIGRACION_V1 = "db/migration/V1__esquema_inicial.sql";

    @Container
    static final PostgreSQLContainer<?> BASE = new PostgreSQLContainer<>("postgres:15-alpine");

    @BeforeAll
    static void migrar() {
        flywayComoLaAplicacion(BASE.getJdbcUrl()).migrate();
    }

    /** La misma configuracion de Flyway que declara application.properties. */
    private static Flyway flywayComoLaAplicacion(String url) {
        return Flyway.configure()
                .dataSource(url, BASE.getUsername(), BASE.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
    }

    private static Connection conectar(String url) throws SQLException {
        return DriverManager.getConnection(url, BASE.getUsername(), BASE.getPassword());
    }

    private static long contar(Connection conexion, String sql) throws SQLException {
        try (Statement sentencia = conexion.createStatement();
             ResultSet filas = sentencia.executeQuery(sql)) {
            filas.next();
            return filas.getLong(1);
        }
    }

    private static String leerRecurso(String ruta) throws IOException {
        try (InputStream entrada = CatalogoInicialIT.class.getClassLoader().getResourceAsStream(ruta)) {
            assertNotNull(entrada, "no esta en el classpath: " + ruta);
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("tras migrar hay 56 productos: 8 heroes, 16 armas, 16 armaduras, 8 items y 8 epicas")
    void dejaLosCincuentaYSeisProductos() throws SQLException {
        Map<String, Long> porTipo = new HashMap<>();
        try (Connection conexion = conectar(BASE.getJdbcUrl());
             Statement sentencia = conexion.createStatement();
             ResultSet filas = sentencia.executeQuery(
                     "SELECT tipo, COUNT(*) FROM productos GROUP BY tipo")) {
            while (filas.next()) {
                porTipo.put(filas.getString(1), filas.getLong(2));
            }
        }

        assertEquals(Map.of(
                "HEROE", 8L,
                "ARMA", 16L,
                "ARMADURA", 16L,
                "ITEM", 8L,
                "EPICA", 8L), porTipo);
    }

    @Test
    @DisplayName("cada producto trae el precio de demostracion de su tipo y sale sin promocion")
    void cadaProductoTraeSuPrecioYSinPromocion() throws SQLException {
        Map<String, BigDecimal> precioEsperado = Map.of(
                "HEROE", new BigDecimal("20000"),
                "EPICA", new BigDecimal("10000"),
                "ARMA", new BigDecimal("6000"),
                "ARMADURA", new BigDecimal("5000"),
                "ITEM", new BigDecimal("3000"));

        try (Connection conexion = conectar(BASE.getJdbcUrl());
             Statement sentencia = conexion.createStatement();
             ResultSet filas = sentencia.executeQuery(
                     "SELECT nombre, tipo, precio_base_cop, en_promocion, porcentaje_descuento, "
                             + "imagen_url, descripcion FROM productos")) {
            int vistas = 0;
            while (filas.next()) {
                vistas++;
                String nombre = filas.getString("nombre");
                String tipo = filas.getString("tipo");
                BigDecimal precio = filas.getBigDecimal("precio_base_cop");
                assertAll(nombre,
                        () -> assertEquals(0, precioEsperado.get(tipo).compareTo(precio),
                                nombre + " cuesta " + precio),
                        () -> assertEquals(Boolean.FALSE, filas.getObject("en_promocion")),
                        () -> assertEquals(0, filas.getInt("porcentaje_descuento")),
                        () -> assertEquals(null, filas.getString("imagen_url")),
                        () -> assertNotNull(filas.getString("descripcion"),
                                "la tarjeta de la tienda pinta la descripcion"));
            }
            assertEquals(56, vistas);
        }
    }

    @Test
    @DisplayName("el equipo dice para que heroe es; las armaduras dicen ademas su parte")
    void elEquipoDiceParaQueHeroeEs() throws SQLException {
        try (Connection conexion = conectar(BASE.getJdbcUrl())) {
            assertAll(
                    () -> assertEquals(48, contar(conexion,
                            "SELECT COUNT(*) FROM productos WHERE tipo <> 'HEROE' "
                                    + "AND habilidades LIKE 'Para %'")),
                    () -> assertEquals(1, contar(conexion,
                            "SELECT COUNT(*) FROM productos WHERE tipo = 'ARMADURA' "
                                    + "AND nombre = 'Defensa del enfurecido' "
                                    + "AND habilidades = 'Para Guerrero Tanque (Pecho)'")),
                    () -> assertEquals(1, contar(conexion,
                            "SELECT COUNT(*) FROM productos WHERE tipo = 'HEROE' "
                                    + "AND nombre = 'Pícaro Veneno' "
                                    + "AND descripcion LIKE '%vida 36%'")));
        }
    }

    @Test
    @DisplayName("correr la insercion otra vez no duplica ningun producto")
    void reejecutarNoDuplica() throws SQLException, IOException {
        String insercion = leerRecurso(MIGRACION_V2);
        try (Connection conexion = conectar(BASE.getJdbcUrl())) {
            try (Statement sentencia = conexion.createStatement()) {
                sentencia.execute(insercion);
                sentencia.execute(insercion);
            }
            assertAll(
                    () -> assertEquals(56, contar(conexion, "SELECT COUNT(*) FROM productos")),
                    () -> assertEquals(56, contar(conexion,
                            "SELECT COUNT(DISTINCT (nombre, tipo)) FROM productos")));
        }
    }

    @Test
    @DisplayName("una base creada antes de Flyway se baselinea en 1 y recibe el catalogo")
    void unaBaseAnteriorAFlywayRecibeElCatalogo() throws SQLException, IOException {
        // Es el caso del servidor: tablas creadas por el antiguo ddl-auto=update,
        // sin historial de Flyway. baseline-on-migrate las marca como version 1
        // (V1 no corre) y V2 debe aplicarse encima.
        try (Connection conexion = conectar(BASE.getJdbcUrl());
             Statement sentencia = conexion.createStatement()) {
            sentencia.execute("CREATE DATABASE anterior_a_flyway");
        }
        String urlAnterior = BASE.getJdbcUrl().replace("/" + BASE.getDatabaseName(), "/anterior_a_flyway");
        try (Connection conexion = conectar(urlAnterior);
             Statement sentencia = conexion.createStatement()) {
            sentencia.execute(leerRecurso(MIGRACION_V1));
        }

        flywayComoLaAplicacion(urlAnterior).migrate();

        try (Connection conexion = conectar(urlAnterior)) {
            assertAll(
                    () -> assertEquals(56, contar(conexion, "SELECT COUNT(*) FROM productos")),
                    () -> assertEquals(1, contar(conexion,
                            "SELECT COUNT(*) FROM flyway_schema_history "
                                    + "WHERE version = '2' AND success")));
        }
    }
}
