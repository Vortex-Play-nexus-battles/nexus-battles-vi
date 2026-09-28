package nexus.inventario.dominio;

/** Solo un HEROE sale de mision (1.6.0, B9): 400 «No es un heroe». */
public class NoEsUnHeroeException extends RuntimeException {

    public NoEsUnHeroeException() {
        super("Solo un heroe puede salir de mision.");
    }
}
