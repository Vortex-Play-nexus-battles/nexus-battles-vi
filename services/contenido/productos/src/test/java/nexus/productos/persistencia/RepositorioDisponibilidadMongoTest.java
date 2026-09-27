package nexus.productos.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.dao.OptimisticLockingFailureException;
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
        Producto producto = productos.insert(producto("limitado", 1));
        CatalogoProductos catalogo = new CatalogoProductos(disponibilidad);
        CountDownLatch preparados = new CountDownLatch(2);
        CountDownLatch salida = new CountDownLatch(1);

        try (ExecutorService ejecutor = Executors.newFixedThreadPool(2)) {
            Future<ResultadoAdquisicion> uno = ejecutor.submit(
                    () -> adquirir(catalogo, producto.id(), "clave-uno", preparados, salida));
            Future<ResultadoAdquisicion> dos = ejecutor.submit(
                    () -> adquirir(catalogo, producto.id(), "clave-dos", preparados, salida));

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
    @DisplayName("B4: N hilos contra un tiraje K: exactamente K reservas aceptadas y el tiraje en cero")
    void nHilosContraTirajeK() throws Exception {
        int tiraje = 7;
        int hilos = 40;
        Producto producto = productos.insert(producto("limitado-k", tiraje));
        CatalogoProductos catalogo = new CatalogoProductos(disponibilidad);
        CountDownLatch preparados = new CountDownLatch(hilos);
        CountDownLatch salida = new CountDownLatch(1);

        List<EstadoAdquisicion> resultados = new ArrayList<>();
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(hilos)) {
            List<Future<ResultadoAdquisicion>> futuros = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                String clave = "compra-" + i;
                futuros.add(ejecutor.submit(() -> adquirir(catalogo, producto.id(), clave, preparados, salida)));
            }
            preparados.await();
            salida.countDown();
            for (Future<ResultadoAdquisicion> futuro : futuros) {
                resultados.add(futuro.get().estado());
            }
        }

        assertEquals(tiraje, resultados.stream().filter(EstadoAdquisicion.ACEPTADA::equals).count());
        assertEquals(hilos - tiraje, resultados.stream().filter(EstadoAdquisicion.AGOTADO::equals).count());
        assertEquals(0, productos.findById(producto.id()).orElseThrow().tiraje());
    }

    @Test
    @DisplayName("B4: la misma clave reservando a la vez desde muchos hilos descuenta una sola unidad")
    void laMismaClaveDescuentaUnaVez() throws Exception {
        int hilos = 20;
        Producto producto = productos.insert(producto("limitado-clave", 5));
        CatalogoProductos catalogo = new CatalogoProductos(disponibilidad);
        CountDownLatch preparados = new CountDownLatch(hilos);
        CountDownLatch salida = new CountDownLatch(1);

        List<EstadoAdquisicion> resultados = new ArrayList<>();
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(hilos)) {
            List<Future<ResultadoAdquisicion>> futuros = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                futuros.add(ejecutor.submit(() -> adquirir(catalogo, producto.id(), "la-misma", preparados, salida)));
            }
            preparados.await();
            salida.countDown();
            for (Future<ResultadoAdquisicion> futuro : futuros) {
                resultados.add(futuro.get().estado());
            }
        }

        assertTrue(resultados.stream().allMatch(EstadoAdquisicion.ACEPTADA::equals), resultados.toString());
        Producto persistido = productos.findById(producto.id()).orElseThrow();
        assertEquals(4, persistido.tiraje());
        assertEquals(List.of("la-misma"), persistido.reservasRecientes());
    }

    @Test
    @DisplayName("B4: repetir una clave que ya reservo responde ACEPTADA aunque el producto se agotara o suspendiera despues")
    void laClaveYaReservadaSigueAceptada() {
        Producto producto = productos.insert(producto("ultima", 1));
        CatalogoProductos catalogo = new CatalogoProductos(disponibilidad);

        assertEquals(EstadoAdquisicion.ACEPTADA, catalogo.adquirir(producto.id(), "la-mia").estado());
        catalogo.suspender(producto.id());

        assertEquals(EstadoAdquisicion.ACEPTADA, catalogo.adquirir(producto.id(), "la-mia").estado());
        assertEquals(EstadoAdquisicion.SUSPENDIDO, catalogo.adquirir(producto.id(), "otra").estado());
        assertEquals(0, productos.findById(producto.id()).orElseThrow().tiraje());
    }

    @Test
    @DisplayName("B4: la ventana de claves recientes no crece sin limite")
    void laVentanaDeClavesTieneTope() {
        Producto producto = productos.insert(producto("ilimitado-ventana", -1));
        CatalogoProductos catalogo = new CatalogoProductos(disponibilidad);

        for (int i = 0; i < RepositorioDisponibilidadMongo.VENTANA_DE_RESERVAS + 5; i++) {
            catalogo.adquirir(producto.id(), "clave-" + i);
        }

        List<String> recientes = productos.findById(producto.id()).orElseThrow().reservasRecientes();
        assertEquals(RepositorioDisponibilidadMongo.VENTANA_DE_RESERVAS, recientes.size());
        assertEquals("clave-" + (RepositorioDisponibilidadMongo.VENTANA_DE_RESERVAS + 4), recientes.getLast());
    }

    @Test
    @DisplayName("B4: suspender y reservar suben la version, asi que una edicion leida antes choca con 409")
    void lasEscriturasParcialesSubenLaVersion() {
        Producto producto = productos.insert(producto("versionado", 5));
        Producto leidoPorUnAdmin = productos.findById(producto.id()).orElseThrow();
        CatalogoProductos catalogo = new CatalogoProductos(disponibilidad);

        catalogo.adquirir(producto.id(), "compra");
        catalogo.suspender(producto.id());

        Producto ahora = productos.findById(producto.id()).orElseThrow();
        assertEquals(leidoPorUnAdmin.version() + 2, ahora.version());
        // Guardar la copia vieja deshacria la suspension y devolveria la unidad
        // vendida: el bloqueo optimista lo impide.
        assertThrows(OptimisticLockingFailureException.class, () -> productos.save(leidoPorUnAdmin));
        assertEquals(EstadoProducto.SUSPENDIDO, productos.findById(producto.id()).orElseThrow().estado());
    }

    @Test
    @DisplayName("el tiraje ilimitado creado por PRD-001 permanece en menos uno")
    void conservaTirajeIlimitado() {
        Producto producto = productos.insert(producto("ilimitado", -1));
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
        Producto producto = productos.insert(producto("agotado", 1));
        CatalogoProductos catalogo = new CatalogoProductos(disponibilidad);

        assertEquals(
                EstadoAdquisicion.ACEPTADA,
                catalogo.adquirir(producto.id()).estado());

        assertTrue(catalogo.consultar(producto.id()).estaAgotado());
    }

    @Test
    @DisplayName("suspender y reactivar conserva el tiraje y el estado anterior en MongoDB")
    void persisteSuspensionYReactivacion() {
        Producto producto = productos.insert(producto(
                "suspendible",
                3,
                EstadoProducto.UNICO));
        CatalogoProductos catalogo = new CatalogoProductos(disponibilidad);

        catalogo.suspender(producto.id());

        Producto suspendido = productos.findById(producto.id()).orElseThrow();
        assertEquals(EstadoProducto.SUSPENDIDO, suspendido.estado());
        assertEquals(EstadoProducto.UNICO, suspendido.estadoAnteriorSuspension());
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

    @Test
    @DisplayName("B4: suspender dos veces no escribe la segunda")
    void suspenderEsIdempotente() {
        Producto producto = productos.insert(producto("dos-veces", 3));
        CatalogoProductos catalogo = new CatalogoProductos(disponibilidad);

        catalogo.suspender(producto.id());
        int versionTrasLaPrimera = productos.findById(producto.id()).orElseThrow().version();
        catalogo.suspender(producto.id());

        assertEquals(versionTrasLaPrimera, productos.findById(producto.id()).orElseThrow().version());
    }

    private static ResultadoAdquisicion adquirir(
            CatalogoProductos catalogo,
            String productoId,
            String clave,
            CountDownLatch preparados,
            CountDownLatch salida) throws InterruptedException {
        preparados.countDown();
        salida.await();
        return catalogo.adquirir(productoId, clave);
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
