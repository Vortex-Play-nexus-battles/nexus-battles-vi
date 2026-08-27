package nexus.productos.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pruebas de aceptación de HU-PRD-002 y HU-PRD-004. */
class CatalogoProductosTest {

    @Test
    @DisplayName("un tiraje positivo registra exactamente las unidades indicadas")
    void registraTirajeLimitado() {
        CatalogoProductos catalogo = catalogoCon("arma-1", 3, EstadoProducto.ACTIVO);

        assertEquals(3, catalogo.consultar("arma-1").unidadesDisponibles());
        assertFalse(catalogo.consultar("arma-1").esIlimitado());
    }

    @Test
    @DisplayName("el valor menos uno identifica un producto ilimitado")
    void registraTirajeIlimitado() {
        CatalogoProductos catalogo = catalogoCon("item-1", -1, EstadoProducto.ACTIVO);

        for (int adquisicion = 0; adquisicion < 20; adquisicion++) {
            assertEquals(EstadoAdquisicion.ACEPTADA, catalogo.adquirir("item-1").estado());
        }

        DisponibilidadProducto producto = catalogo.consultar("item-1");
        assertTrue(producto.esIlimitado());
        assertEquals(-1, producto.unidadesDisponibles());
    }

    @Test
    @DisplayName("un tiraje inicial distinto de menos uno o de un positivo se rechaza")
    void rechazaTirajeInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> DisponibilidadProducto.nueva("arma-1", 0, EstadoProducto.ACTIVO));
        assertThrows(IllegalArgumentException.class,
                () -> DisponibilidadProducto.nueva("arma-1", -2, EstadoProducto.ACTIVO));
    }

    @Test
    @DisplayName("un producto agotado impide la adquisición e informa la causa")
    void impideAdquirirProductoAgotado() {
        CatalogoProductos catalogo = catalogoCon("arma-1", 1, EstadoProducto.ACTIVO);

        ResultadoAdquisicion primera = catalogo.adquirir("arma-1");
        ResultadoAdquisicion segunda = catalogo.adquirir("arma-1");

        assertEquals(EstadoAdquisicion.ACEPTADA, primera.estado());
        assertEquals(EstadoAdquisicion.AGOTADO, segunda.estado());
        assertTrue(segunda.mensaje().toLowerCase().contains("agotado"));
        assertEquals(0, catalogo.consultar("arma-1").unidadesDisponibles());
    }

    @Test
    @DisplayName("dos adquisiciones simultáneas de la última unidad producen un solo éxito")
    void controlaConcurrenciaSobreUltimaUnidad() throws Exception {
        CatalogoProductos catalogo = catalogoCon("epica-1", 1, EstadoProducto.UNICO);
        CountDownLatch preparados = new CountDownLatch(2);
        CountDownLatch salida = new CountDownLatch(1);

        try (ExecutorService ejecutor = Executors.newFixedThreadPool(2)) {
            Future<ResultadoAdquisicion> uno = ejecutor.submit(
                    () -> adquirirAlMismoTiempo(catalogo, preparados, salida));
            Future<ResultadoAdquisicion> dos = ejecutor.submit(
                    () -> adquirirAlMismoTiempo(catalogo, preparados, salida));

            preparados.await();
            salida.countDown();
            List<EstadoAdquisicion> resultados = List.of(
                    uno.get().estado(), dos.get().estado());

            assertEquals(1L, resultados.stream()
                    .filter(EstadoAdquisicion.ACEPTADA::equals)
                    .count());
            assertEquals(1L, resultados.stream()
                    .filter(EstadoAdquisicion.AGOTADO::equals)
                    .count());
        }
    }

    @Test
    @DisplayName("suspender conserva el registro y bloquea nuevas adquisiciones")
    void suspendeSinEliminarNiAfectarExistencias() {
        RepositorioDisponibilidadEnMemoria repositorio = new RepositorioDisponibilidadEnMemoria();
        CatalogoProductos catalogo = new CatalogoProductos(repositorio);
        catalogo.registrar(DisponibilidadProducto.nueva(
                "armadura-1", 4, EstadoProducto.ACTIVO));

        DisponibilidadProducto suspendido = catalogo.suspender("armadura-1");
        ResultadoAdquisicion resultado = catalogo.adquirir("armadura-1");

        assertEquals(EstadoProducto.SUSPENDIDO, suspendido.estado());
        assertEquals(EstadoProducto.ACTIVO, suspendido.estadoAlReactivar());
        assertEquals(EstadoAdquisicion.SUSPENDIDO, resultado.estado());
        assertTrue(repositorio.buscarPorId("armadura-1").isPresent());
        assertEquals(4, catalogo.consultar("armadura-1").unidadesDisponibles());
    }

    @Test
    @DisplayName("reactivar restaura la disponibilidad anterior del producto")
    void reactivaProductoSuspendido() {
        CatalogoProductos catalogo = catalogoCon("epica-1", 1, EstadoProducto.UNICO);
        catalogo.suspender("epica-1");

        DisponibilidadProducto reactivado = catalogo.reactivar("epica-1");
        ResultadoAdquisicion resultado = catalogo.adquirir("epica-1");

        assertEquals(EstadoProducto.UNICO, reactivado.estado());
        assertEquals(EstadoAdquisicion.ACEPTADA, resultado.estado());
    }

    @Test
    @DisplayName("consultar, suspender o reactivar un producto inexistente se rechaza")
    void rechazaProductoInexistente() {
        CatalogoProductos catalogo = new CatalogoProductos(
                new RepositorioDisponibilidadEnMemoria());

        assertThrows(ProductoNoEncontradoException.class,
                () -> catalogo.consultar("no-existe"));
        assertThrows(ProductoNoEncontradoException.class,
                () -> catalogo.suspender("no-existe"));
        assertThrows(ProductoNoEncontradoException.class,
                () -> catalogo.reactivar("no-existe"));
        assertEquals(EstadoAdquisicion.NO_ENCONTRADO,
                catalogo.adquirir("no-existe").estado());
    }

    private static CatalogoProductos catalogoCon(
            String productoId,
            int tiraje,
            EstadoProducto estado) {
        CatalogoProductos catalogo = new CatalogoProductos(
                new RepositorioDisponibilidadEnMemoria());
        catalogo.registrar(DisponibilidadProducto.nueva(productoId, tiraje, estado));
        return catalogo;
    }

    private static ResultadoAdquisicion adquirirAlMismoTiempo(
            CatalogoProductos catalogo,
            CountDownLatch preparados,
            CountDownLatch salida) throws InterruptedException {
        preparados.countDown();
        salida.await();
        return catalogo.adquirir("epica-1");
    }
}
