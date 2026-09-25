package nexus.misiones.dominio;

/**
 * No hay una ejecucion de ESTE jugador con ese identificador (404). La de otro
 * jugador responde igual que si no existiera: no se confirma lo ajeno.
 */
public class EjecucionNoEncontrada extends RuntimeException {

    public EjecucionNoEncontrada() {
        super("No encontramos esa misión entre las tuyas.");
    }
}
