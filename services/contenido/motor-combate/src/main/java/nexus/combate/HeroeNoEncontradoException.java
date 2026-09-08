package nexus.combate;

public class HeroeNoEncontradoException extends ClienteHeroesException {

    public HeroeNoEncontradoException(String nombreHeroe, String detalle) {
        super("Heroe '" + nombreHeroe + "' no encontrado: " + detalle);
    }
}
