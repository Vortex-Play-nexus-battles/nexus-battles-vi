package nexus.inventario.aplicacion;

/** El inventario debe migrarse antes de exponerlo mediante el contrato UUID. */
public class IdentificadorHistoricoException extends RuntimeException {

    public IdentificadorHistoricoException() {
        super("El inventario conserva identificadores antiguos y debe migrarse a UUID.");
    }
}
