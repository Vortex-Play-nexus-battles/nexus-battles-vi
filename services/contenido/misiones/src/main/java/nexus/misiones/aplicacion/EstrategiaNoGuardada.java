package nexus.misiones.aplicacion;

/** El jugador no tiene estrategia guardada para ese heroe (404). */
public class EstrategiaNoGuardada extends RuntimeException {

    public EstrategiaNoGuardada() {
        super("Todavía no guardaste una estrategia para este héroe.");
    }
}
