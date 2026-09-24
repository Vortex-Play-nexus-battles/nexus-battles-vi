package nexus.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import nexus.api.PaginaDeProductos;
import nexus.api.ProductoCreado;
import nexus.aplicacion.ListarProductosServicio;
import nexus.aplicacion.ProductoMapper;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@DataMongoTest
@Testcontainers
class ProductoRepositoryTest {

        @Container
        @ServiceConnection
        static final MongoDBContainer MONGODB =
                new MongoDBContainer("mongo:8.0");

        @Autowired
        private ProductoRepository repositorio;

        @BeforeEach
        void limpiarBaseDeDatos() {
                repositorio.deleteAll();
        }

        @Test
        @DisplayName("guarda y recupera un producto en MongoDB")
        void guardaYRecuperaProducto() {
                Instant ahora = Instant.parse("2026-08-27T18:00:00Z");

                Producto producto = new Producto(
                        "550e8400-e29b-41d4-a716-446655440000",
                        "Espada solar",
                        "productos/espada-solar.webp",
                        "Arma guardada en MongoDB",
                        TipoProducto.ARMA,
                        100,
                        500,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        40,
                        new BigDecimal("12.5"),
                        EstadoProducto.ACTIVO,
                        1,
                        ahora,
                        ahora);

                repositorio.save(producto);

                Producto recuperado = repositorio
                        .findById(producto.id())
                        .orElseThrow();

                assertEquals(producto.id(), recuperado.id());
                assertEquals("Espada solar", recuperado.nombre());
                assertEquals(TipoProducto.ARMA, recuperado.tipo());
                assertEquals(500, recuperado.precioCreditos());
                assertEquals(40, recuperado.poderDeAtaque());
                assertEquals(
                        0,
                        new BigDecimal("12.5")
                                .compareTo(recuperado.tasaDeCaida()));
                assertEquals(EstadoProducto.ACTIVO, recuperado.estado());
                assertEquals(1, recuperado.version());
                assertEquals(ahora, recuperado.creadoEn());
                assertEquals(ahora, recuperado.modificadoEn());
                assertTrue(repositorio.existsById(producto.id()));
                assertEquals(1, repositorio.count());
                assertEquals(1, repositorio.countByTipo(TipoProducto.ARMA));
                assertEquals(1, repositorio.countByEstado(EstadoProducto.ACTIVO));
        }

        // R16 — el listado del catalogo contra MongoDB real. Las consultas
        // derivadas (tipo + estado $in), el orden por creadoEn/_id y los totales
        // de la pagina solo se comprueban de verdad aqui; ListarProductosServicioTest
        // fija que argumentos se piden, esto fija que esos argumentos devuelven
        // lo que promete el contrato 1.2.0.

        @Test
        @DisplayName("el listado real deja fuera SUSPENDIDO, pagina y ordena de forma estable")
        void listaElCatalogoRealPaginadoYEnOrdenEstable() {
                guardarCatalogoDePrueba();
                ListarProductosServicio listado = listadoReal();

                PaginaDeProductos primera = listado.listar(0, 2, null, null);
                PaginaDeProductos segunda = listado.listar(1, 2, null, null);

                // p-2 y p-5 comparten fecha de creacion: desempata el id.
                assertEquals(List.of("p-1", "p-2"), ids(primera));
                assertEquals(List.of("p-5", "p-4"), ids(segunda));
                assertEquals(4L, primera.totalElements());
                assertEquals(2, primera.totalPages());
                assertEquals(0, primera.page());
                assertEquals(1, segunda.page());
                assertEquals(2, segunda.size());
        }

        @Test
        @DisplayName("el listado real filtra por tipo y solo muestra SUSPENDIDO si se pide")
        void filtraPorTipoYEstadoEnMongo() {
                guardarCatalogoDePrueba();
                ListarProductosServicio listado = listadoReal();

                PaginaDeProductos armas = listado.listar(0, 10, TipoProducto.ARMA, null);
                PaginaDeProductos suspendidos =
                        listado.listar(0, 10, null, EstadoProducto.SUSPENDIDO);
                PaginaDeProductos armasSuspendidas =
                        listado.listar(0, 10, TipoProducto.ARMA, EstadoProducto.SUSPENDIDO);

                assertEquals(List.of("p-1", "p-5"), ids(armas));
                assertEquals(2L, armas.totalElements());
                assertEquals(List.of("p-3"), ids(suspendidos));
                assertEquals(List.of("p-3"), ids(armasSuspendidas));
        }

        /**
         * Cinco productos: cuatro listables por omision (p-1, p-2, p-4, p-5) y
         * uno suspendido (p-3). p-2 y p-5 se crean en el mismo instante para
         * que el desempate por identificador quede a la vista.
         */
        private void guardarCatalogoDePrueba() {
                Instant base = Instant.parse("2026-09-20T12:00:00Z");
                repositorio.saveAll(List.of(
                        productoDelListado("p-1", TipoProducto.ARMA,
                                EstadoProducto.ACTIVO, base),
                        productoDelListado("p-2", TipoProducto.HEROE,
                                EstadoProducto.UNICO, base.plusSeconds(60)),
                        productoDelListado("p-3", TipoProducto.ARMA,
                                EstadoProducto.SUSPENDIDO, base.plusSeconds(120)),
                        productoDelListado("p-4", TipoProducto.ITEM,
                                EstadoProducto.ACTIVO, base.plusSeconds(180)),
                        productoDelListado("p-5", TipoProducto.ARMA,
                                EstadoProducto.ACTIVO, base.plusSeconds(60))));
        }

        private ListarProductosServicio listadoReal() {
                return new ListarProductosServicio(
                        repositorio,
                        Mappers.getMapper(ProductoMapper.class));
        }

        private static List<String> ids(PaginaDeProductos pagina) {
                return pagina.content().stream().map(ProductoCreado::id).toList();
        }

        private static Producto productoDelListado(
                        String id,
                        TipoProducto tipo,
                        EstadoProducto estado,
                        Instant creadoEn) {
                return new Producto(
                        id,
                        "Producto " + id,
                        "productos/" + id + ".webp",
                        "Producto para verificar el listado en MongoDB",
                        tipo,
                        100,
                        500,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        40,
                        new BigDecimal("12.5"),
                        estado,
                        1,
                        creadoEn,
                        creadoEn);
        }
}
