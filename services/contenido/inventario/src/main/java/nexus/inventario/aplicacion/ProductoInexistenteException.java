package nexus.inventario.aplicacion;

public class ProductoInexistenteException extends RuntimeException {

    public ProductoInexistenteException() {
        super("El producto no existe en el catalogo.");
    }
}
