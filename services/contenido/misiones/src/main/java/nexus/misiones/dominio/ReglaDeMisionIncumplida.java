package nexus.misiones.dominio;

/**
 * Algo del estado del juego impide la operacion (409): la mision esta
 * bloqueada, ya esta en curso, el heroe esta ocupado... El mensaje es apto para
 * el jugador y viaja tal cual en el {@code detail} del problem detail.
 */
public class ReglaDeMisionIncumplida extends RuntimeException {

    public ReglaDeMisionIncumplida(String detalleParaElJugador) {
        super(detalleParaElJugador);
    }
}
