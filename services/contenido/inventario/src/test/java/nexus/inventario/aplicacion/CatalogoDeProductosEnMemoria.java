package nexus.inventario.aplicacion;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;

/**
 * Doble del servicio de productos: solo existe lo que la prueba registra.
 *
 * <p>Desde que el inventario valida contra el catalogo, crear un elemento con
 * un producto que nadie registro falla igual que en produccion. Las pruebas
 * que antes usaban ids arbitrarios ("producto-1") los registran aqui con el
 * tipo que ya declaraban: el comportamiento que verifican no cambia.
 *
 * <p>B4: una armadura se registra con su parte ({@link #registrarArmadura}),
 * como la describe el catalogo; y se cuentan las consultas, para comprobar que
 * un reintento no vuelve a preguntar lo que ya decidio.
 */
public class CatalogoDeProductosEnMemoria implements ResolutorDeProducto {

    private final Map<String, DetalleProducto> productos = new HashMap<>();
    private final AtomicInteger consultas = new AtomicInteger();
    private boolean caido;

    public CatalogoDeProductosEnMemoria registrar(String productoId, TipoElementoInventario tipo) {
        return registrar(productoId, tipo, "ACTIVO");
    }

    public CatalogoDeProductosEnMemoria registrar(
            String productoId, TipoElementoInventario tipo, String estado) {
        productos.put(productoId, new DetalleProducto(productoId, tipo.name(), null, estado));
        return this;
    }

    /** Una armadura con la parte del cuerpo que ocupa segun el catalogo. */
    public CatalogoDeProductosEnMemoria registrarArmadura(String productoId, ParteArmadura parte) {
        return registrarArmadura(productoId, parte == null ? null : parte.name(), "ACTIVO");
    }

    public CatalogoDeProductosEnMemoria registrarArmadura(String productoId, String parte, String estado) {
        productos.put(productoId, new DetalleProducto(
                "Armadura " + productoId, TipoElementoInventario.ARMADURA.name(), null, estado, parte));
        return this;
    }

    /** Un producto con todos sus datos, tal como lo responderia el catalogo. */
    public CatalogoDeProductosEnMemoria registrar(String productoId, DetalleProducto detalle) {
        productos.put(productoId, detalle);
        return this;
    }

    /** Simula que el servicio de productos no responde. */
    public void caer() {
        caido = true;
    }

    /** El servicio de productos vuelve a responder. */
    public void levantar() {
        caido = false;
    }

    public int consultas() {
        return consultas.get();
    }

    @Override
    public DetalleProducto resolver(String productoId) {
        consultas.incrementAndGet();
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
