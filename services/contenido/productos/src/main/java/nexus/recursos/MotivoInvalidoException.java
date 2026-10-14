package nexus.recursos;

/** Un motivo que no cabe donde se guarda. 400. */
public class MotivoInvalidoException extends RuntimeException {

    public MotivoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
