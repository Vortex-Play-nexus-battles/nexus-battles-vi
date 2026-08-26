package nexus.inventario.dominio;

public class ElementoNoEncontradoException extends RuntimeException {

    public ElementoNoEncontradoException() {
        super("El elemento solicitado no existe en el inventario.");
    }
}
