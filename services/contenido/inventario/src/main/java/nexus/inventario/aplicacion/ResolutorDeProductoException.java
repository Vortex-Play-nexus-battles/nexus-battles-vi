package nexus.inventario.aplicacion;

public class ResolutorDeProductoException extends RuntimeException {

    public ResolutorDeProductoException(String mensaje) {
        super(mensaje);
    }

    public ResolutorDeProductoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
