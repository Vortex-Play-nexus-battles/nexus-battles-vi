package nexus.inventario.aplicacion;

public class PrototipoDeHeroeNoEncontradoException extends ResolutorDeEstadisticasHeroeException {

    public PrototipoDeHeroeNoEncontradoException(String prototipo, String detalle) {
        super("Prototipo de heroe '" + prototipo + "' no encontrado: " + detalle);
    }
}
