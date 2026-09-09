package nexus.inventario.aplicacion;

public class CriterioBusquedaInvalidoException extends RuntimeException {

    public CriterioBusquedaInvalidoException() {
        super("Ingresa al menos cuatro caracteres para buscar.");
    }
}
