package nexus.inventario.aplicacion;

public class TipoNoCoincideException extends RuntimeException {

    public TipoNoCoincideException() {
        super("El tipo no coincide con el producto del catalogo.");
    }
}
