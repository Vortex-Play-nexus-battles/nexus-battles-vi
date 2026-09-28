package nexus.misiones.dominio;

import java.util.Objects;

/**
 * Un enemigo Master (seccion 7.8.4): aparece al azar segun su probabilidad,
 * esta «dos niveles arriba del jugador» (6.1.2) y al derrotarlo entrega su
 * epica.
 *
 * @param probabilidad de aparecer en la mision, como proporcion (0,15 = 15 %)
 */
public record MasterDeMision(String nombre, String prototipo, double probabilidad, Epica epica) {

    /** «Siempre tendran dos niveles arriba del jugador» (seccion 6.1.2). */
    public static final int NIVELES_POR_ENCIMA = 2;

    public MasterDeMision {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Un Master necesita nombre.");
        }
        if (prototipo == null || prototipo.isBlank()) {
            throw new IllegalArgumentException("El Master «" + nombre + "» necesita un prototipo del catalogo.");
        }
        if (probabilidad < 0 || probabilidad > 1) {
            throw new IllegalArgumentException("La probabilidad del Master «" + nombre + "» va de 0 a 1.");
        }
        Objects.requireNonNull(epica, "Un Master sin epica no es un Master.");
    }

    /** Nivel del Master frente a un heroe: dos por encima, con el tope 8 de 6.1.1. */
    public static int nivelFrente(int nivelDelHeroe) {
        return Math.min(HeroeEnMision.NIVEL_MAXIMO, nivelDelHeroe + NIVELES_POR_ENCIMA);
    }
}
