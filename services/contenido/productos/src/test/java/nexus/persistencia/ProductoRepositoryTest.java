package nexus.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;

import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
}
