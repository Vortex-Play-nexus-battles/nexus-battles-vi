package nexus.productos.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import nexus.productos.dominio.CatalogoProductos;
import nexus.productos.dominio.EstadoAdquisicion;
import nexus.productos.dominio.RepositorioDisponibilidadProductos;
import nexus.productos.dominio.ResultadoAdquisicion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@DataMongoTest
@Testcontainers
@Import(RepositorioDisponibilidadMongo.class)
class RepositorioDisponibilidadMongoTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGODB =
            new MongoDBContainer("mongo:8.0");

    @Autowired
    private ProductoRepository productos;

    @Autowired
    private RepositorioDisponibilidadProductos disponibilidad;

    @BeforeEach
    void limpiarBaseDeDatos() {
        productos.deleteAll();
    }

    @Test
    @DisplayName("la última unidad se reserva una sola vez en MongoDB")
    void reservaAtomicamenteLaUltimaUnidad() throws Exception {
        Producto producto = productos.save(producto("limitado", 1));
        CatalogoProductos catalogo = new CatalogoProductos(disponibilidad);
        CountDownLatch preparados = new CountDownLatch(2);
        CountDownLatch salida = new CountDownLatch(1);

        try (ExecutorService ejecutor = Executors.newFixedThreadPool(2)) {
            Future<ResultadoAdquisicion> uno = ejecutor.submit(
                    () -> adquirir(catalogo, producto.id(), preparados, salida));
            Future<ResultadoAdquisicion> dos = ejecutor.submit(
                    () -> adquirir(catalogo, producto.id(), preparados, salida));

            preparados.await();
            salida.countDown();
            List<EstadoAdquisicion> resultados = List.of(
                    uno.get().estado(),
                    dos.get().estado());

            assertEquals(1L, resultados.stream()
                    .filter(EstadoAdquisicion.ACEPTADA::equals)
                    .count());
            assertEquals(1L, resultados.stream()
                    .filter(EstadoAdquisicion.AGOTADO::equals)
                    .count());
        }

        assertEquals(0, productos.findById(producto.id()).orElseThrow().tiraje());
    }

    @Test
    @DisplayName("el tiraje ilimitado creado por PRD-001 permanece en menos uno")
    void conservaTirajeIlimitado() {
        Producto producto = productos.save(producto("ilimitado", -1));
        CatalogoProductos catalogo = new CatalogoProductos(disponibilidad);

        for (int intento = 0; intento < 10; intento++) {
            assertEquals(
                    EstadoAdquisicion.ACEPTADA,
                    catalogo.adquirir(producto.id()).estado());
        }

        Producto persistido = productos.findById(producto.id()).orElseThrow();
        assertEquals(-1, persistido.tiraje());
        assertTrue(catalogo.consultar(producto.id()).esIlimitado());
    }

    @Test
    @DisplayName("un producto agotado se puede consultar después de reservar la última unidad")
    void consultaProductoAgotado() {
        Producto producto = productos.save(producto("agotado", 1));
        CatalogoProductos catalogo = new CatalogoProductos(disponibilidad);

        assertEquals(
                EstadoAdquisicion.ACEPTADA,
                catalogo.adquirir(producto.id()).estado());

        assertTrue(catalogo.consultar(producto.id()).estaAgotado());
    }

    @Test
    @DisplayName("suspender y reactivar conserva el tiraje y el estado anterior en MongoDB")
    void persisteSuspensionYReactivacion() {
        Producto producto = productos.save(producto(
                "suspendible",
                3,
                EstadoProducto.UNICO));
        CatalogoProductos catalogo = new CatalogoProductos(disponibilidad);

        catalogo.suspender(producto.id());

        Producto suspendido = productos.findById(producto.id()).orElseThrow();
        assertEquals(EstadoProducto.SUSPENDIDO, suspendido.estado());
        assertEquals(3, suspendido.tiraje());
        assertEquals(
                EstadoAdquisicion.SUSPENDIDO,
                catalogo.adquirir(producto.id()).estado());

        assertEquals(
                EstadoProducto.UNICO,
                catalogo.reactivar(producto.id()).estado());
        assertEquals(
                EstadoAdquisicion.ACEPTADA,
                catalogo.adquirir(producto.id()).estado());

        Producto reactivado = productos.findById(producto.id()).orElseThrow();
        assertEquals(EstadoProducto.UNICO, reactivado.estado());
        assertEquals(2, reactivado.tiraje());
    }

    private static ResultadoAdquisicion adquirir(
            CatalogoProductos catalogo,
            String productoId,
            CountDownLatch preparados,
            CountDownLatch salida) throws InterruptedException {
        preparados.countDown();
        salida.await();
        return catalogo.adquirir(productoId);
    }

    private static Producto producto(String id, int tiraje) {
        return producto(id, tiraje, EstadoProducto.ACTIVO);
    }

    private static Producto producto(
            String id,
            int tiraje,
            EstadoProducto estado) {
        Instant ahora = Instant.parse("2026-09-01T18:00:00Z");
        return new Producto(
                id,
                "Producto " + id,
                "productos/" + id + ".webp",
                "Producto para validar disponibilidad",
                TipoProducto.ARMA,
                tiraje,
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
                ahora,
                ahora);
    }
}
