package nexus.dominio;

/**
 * Nivel fuera de 1..8 (seccion 6.1.1: "El nivel inicial de todos los personajes
 * es uno (1) y puede incrementarse hasta el nivel 8"). El mensaje es apto para
 * el usuario final, como exige el cliente para todo error visible.
 */
public class NivelFueraDeRangoException extends IllegalArgumentException {

    public NivelFueraDeRangoException() {
        super("El nivel de un héroe está entre 1 y " + Heroe.NIVEL_MAXIMO + ".");
    }
}
