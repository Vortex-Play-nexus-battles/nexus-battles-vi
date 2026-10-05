package nexus.inventario.aplicacion;

public class ProductoSuspendidoException extends RuntimeException {

    public ProductoSuspendidoException() {
        super("El producto esta suspendido en el catalogo y no se puede agregar al inventario.");
    }
}
