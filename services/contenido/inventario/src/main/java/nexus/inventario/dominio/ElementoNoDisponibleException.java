package nexus.inventario.dominio;

public class ElementoNoDisponibleException extends RuntimeException {

    public ElementoNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
