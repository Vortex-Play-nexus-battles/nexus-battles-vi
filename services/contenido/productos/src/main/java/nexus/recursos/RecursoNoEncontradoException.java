package nexus.recursos;

/** No hay recurso gráfico con ese identificador. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException() {
        super("No existe ese recurso gráfico.");
    }
}
