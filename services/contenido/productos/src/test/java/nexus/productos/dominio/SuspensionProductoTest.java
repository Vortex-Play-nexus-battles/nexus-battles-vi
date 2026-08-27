package nexus.productos.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pruebas de aceptación de HU-PRD-004. */
class SuspensionProductoTest {

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
    @DisplayName("suspender o reactivar un producto inexistente se rechaza")
    void rechazaProductoInexistente() {
        CatalogoProductos catalogo = new CatalogoProductos(
                new RepositorioDisponibilidadEnMemoria());

        assertThrows(ProductoNoEncontradoException.class,
                () -> catalogo.suspender("no-existe"));
        assertThrows(ProductoNoEncontradoException.class,
                () -> catalogo.reactivar("no-existe"));
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
}
