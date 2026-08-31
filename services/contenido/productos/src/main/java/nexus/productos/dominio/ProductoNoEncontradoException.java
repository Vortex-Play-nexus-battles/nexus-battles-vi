package nexus.productos.dominio;

public final class ProductoNoEncontradoException extends RuntimeException {

    public ProductoNoEncontradoException(String productoId) {
        super("No existe el producto " + productoId);
    }
}
