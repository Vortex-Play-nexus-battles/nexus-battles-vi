package nexus.inventario.aplicacion;

public class IdentidadRequeridaException extends RuntimeException {

    public IdentidadRequeridaException() {
        super("Debes autenticarte para operar sobre tu inventario.");
    }
}
