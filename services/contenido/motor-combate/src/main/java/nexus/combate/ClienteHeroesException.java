package nexus.combate;

public class ClienteHeroesException extends RuntimeException {

    public ClienteHeroesException(String mensaje) {
        super(mensaje);
    }

    public ClienteHeroesException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
