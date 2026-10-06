package nexus.misiones.dominio;

import java.util.Objects;

/**
 * Un enemigo Master (seccion 7.8.4): aparece al azar segun su probabilidad,
 * esta «dos niveles arriba del jugador» (6.1.2) y al derrotarlo entrega su
 * epica.
 *
 * <p>Como el jefe, un Master puede fijar su vida y su defensa. Ningun Master del
 * documento ni de la Tabla 20 lo hace: pelean con las de su prototipo en su
 * nivel, que es lo que los hace «claramente mas fuertes» (HU-SIM-006). Fijarlas
 * existe para la semilla extra del banco E2E, donde un Master de verdad nunca
 * perderia contra el heroe del kit (Guerrero Tanque de nivel 1).
 *
 * @param probabilidad de aparecer en la mision, como proporcion (0,15 = 15 %)
 * @param vida         la que pelea; nula = la del prototipo en su nivel
 * @param defensa      nula = la del prototipo en su nivel
 */
public record MasterDeMision(String nombre, String prototipo, double probabilidad, Integer vida, Integer defensa,
                             Epica epica) {

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
        if (vida != null && vida < 1) {
            throw new IllegalArgumentException("La vida del Master «" + nombre + "» no puede bajar de 1.");
        }
        if (defensa != null && defensa < 0) {
            throw new IllegalArgumentException("La defensa del Master «" + nombre + "» no puede ser negativa.");
        }
        Objects.requireNonNull(epica, "Un Master sin epica no es un Master.");
    }

    /** Un Master que pelea con la vida y la defensa de su prototipo: todos los del documento. */
    public MasterDeMision(String nombre, String prototipo, double probabilidad, Epica epica) {
        this(nombre, prototipo, probabilidad, null, null, epica);
    }

    /** Nivel del Master frente a un heroe: dos por encima, con el tope 8 de 6.1.1. */
    public static int nivelFrente(int nivelDelHeroe) {
        return Math.min(HeroeEnMision.NIVEL_MAXIMO, nivelDelHeroe + NIVELES_POR_ENCIMA);
    }
}
