package nexus.misiones.dominio;

import java.util.Objects;

/**
 * El heroe tal como salio a la mision: una foto tomada al matricular.
 *
 * <p>La foto no es una copia del inventario que se pueda desincronizar: el
 * heroe queda bloqueado desde ese momento y «no puede ser modificado su
 * equipamiento» (7.8.10), asi que sus estadisticas no cambian hasta que
 * vuelva. Guardarla es lo que permite simular al vencer el plazo sin volver a
 * preguntar a nadie, y reportar «heroe utilizado y nivel» (7.8.8).
 *
 * @param poder   poder maximo con el equipamiento aplicado
 * @param vida    vida maxima con el equipamiento aplicado
 * @param defensa defensa con el equipamiento aplicado
 */
public record HeroeEnMision(
        String id,
        String nombre,
        String prototipo,
        String productoId,
        int nivel,
        double experiencia,
        int poder,
        int vida,
        int defensa) {

    /** Seccion 6.1.1: el nivel va de 1 a 8. */
    public static final int NIVEL_MINIMO = 1;
    public static final int NIVEL_MAXIMO = 8;

    public HeroeEnMision {
        Objects.requireNonNull(id, "El heroe necesita su identificador de inventario.");
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El heroe necesita nombre.");
        }
        if (prototipo == null || prototipo.isBlank()) {
            throw new IllegalArgumentException("El heroe necesita su prototipo del catalogo.");
        }
        if (nivel < NIVEL_MINIMO || nivel > NIVEL_MAXIMO) {
            throw new IllegalArgumentException("El nivel de un heroe va de 1 a 8.");
        }
        if (vida < 1) {
            throw new IllegalArgumentException("Un heroe sin vida no sale a una mision.");
        }
        if (poder < 0 || defensa < 0 || experiencia < 0) {
            throw new IllegalArgumentException("Estadisticas negativas.");
        }
    }
}
