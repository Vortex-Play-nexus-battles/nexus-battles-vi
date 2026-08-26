package nexus.inventario.aplicacion;

public class InventarioAjenoException extends RuntimeException {

    public InventarioAjenoException() {
        super("No tienes permiso sobre ese inventario.");
    }
}
