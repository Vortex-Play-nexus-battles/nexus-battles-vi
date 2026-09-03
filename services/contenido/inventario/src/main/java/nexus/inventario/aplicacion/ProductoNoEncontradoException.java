package nexus.inventario.aplicacion;

public class ProductoNoEncontradoException extends ResolutorDeProductoException {

    public ProductoNoEncontradoException(String productoId, String detalle) {
        super("Producto '" + productoId + "' no encontrado: " + detalle);
    }
}
