package nexus.combate;

public final class IntegracionBotinException extends RuntimeException {

    public IntegracionBotinException(String mensaje) {
        super(mensaje);
    }

    public IntegracionBotinException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
