package nexus.inventario.aplicacion;

/**
 * HU-SAL-003: el jugador tiene mas de un heroe en su inventario, y todavia
 * no existe ningun mecanismo de seleccion explicita de "cual es el heroe
 * activo". No se adivina cual mostrar — se falla explicito con 409 hasta
 * que esa seleccion exista.
 */
public class SeleccionDeHeroeActivoNoDefinidaException extends RuntimeException {

    public SeleccionDeHeroeActivoNoDefinidaException() {
        super("El jugador tiene mas de un heroe en su inventario. "
                + "El mecanismo de seleccion explicita del heroe activo todavia no existe.");
    }
}
