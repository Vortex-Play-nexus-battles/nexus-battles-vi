package nexus.inventario.aplicacion;

import java.util.HashMap;
import java.util.Map;
import nexus.inventario.dominio.TipoElementoInventario;

/**
 * Doble del servicio de productos: solo existe lo que la prueba registra.
 *
 * <p>Desde que el inventario valida contra el catalogo, crear un elemento con
 * un producto que nadie registro falla igual que en produccion. Las pruebas
 * que antes usaban ids arbitrarios ("producto-1") los registran aqui con el
 * tipo que ya declaraban: el comportamiento que verifican no cambia.
 */
public class CatalogoDeProductosEnMemoria implements ResolutorDeProducto {

    private final Map<String, DetalleProducto> productos = new HashMap<>();
    private boolean caido;

    public CatalogoDeProductosEnMemoria registrar(String productoId, TipoElementoInventario tipo) {
        return registrar(productoId, tipo, "ACTIVO");
    }

    public CatalogoDeProductosEnMemoria registrar(
            String productoId, TipoElementoInventario tipo, String estado) {
        productos.put(productoId, new DetalleProducto(productoId, tipo.name(), null, estado));
        return this;
    }

    /** Simula que el servicio de productos no responde. */
    public void caer() {
        caido = true;
    }

    @Override
    public DetalleProducto resolver(String productoId) {
        if (caido) {
            throw new ResolutorDeProductoException("No se pudo contactar al servicio de productos");
        }
        DetalleProducto detalle = productos.get(productoId);
        if (detalle == null) {
            throw new ProductoNoEncontradoException(productoId, "No existe");
        }
        return detalle;
    }
}
