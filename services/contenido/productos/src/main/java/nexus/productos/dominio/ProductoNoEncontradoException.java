package nexus.productos.dominio;

/** Indica que una operación administrativa apuntó a un producto inexistente. */
public final class ProductoNoEncontradoException extends RuntimeException {

    public ProductoNoEncontradoException(String productoId) {
        super("No existe el producto " + productoId);
    }
}
