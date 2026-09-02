package nexus.inventario.dominio;

public class ElementoYaEquipadoException extends RuntimeException {

    public ElementoYaEquipadoException() {
        super("El elemento ya esta equipado");
    }
}
