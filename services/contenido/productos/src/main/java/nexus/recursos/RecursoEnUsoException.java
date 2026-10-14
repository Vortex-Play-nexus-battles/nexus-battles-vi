package nexus.recursos;

/**
 * Retirar un recurso que es la imagen actual de un producto dejaría al
 * producto sin imagen: primero se le pone otra.
 */
public class RecursoEnUsoException extends RuntimeException {

    public RecursoEnUsoException(String nombreDelProducto) {
        super("Es la imagen actual de «" + nombreDelProducto + "». Ponle otra imagen antes de retirarla.");
    }
}
