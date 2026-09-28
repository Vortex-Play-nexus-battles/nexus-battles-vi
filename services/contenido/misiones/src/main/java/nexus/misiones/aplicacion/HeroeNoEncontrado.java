package nexus.misiones.aplicacion;

/**
 * El heroe no existe o no es del jugador (404). Las dos cosas responden igual a
 * proposito: al jugador no se le confirma que un heroe ajeno existe.
 */
public class HeroeNoEncontrado extends RuntimeException {

    public HeroeNoEncontrado() {
        super("No encontramos ese héroe en tu inventario.");
    }
}
