package nexus.misiones.ia;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * La copia local de la Tabla 7 (6.1.2): que acciones son de cada prototipo y desde que nivel las tiene. Es lo unico
 * de la tabla que misiones necesita saber sin preguntarle a heroes —por ejemplo, para validar las estrategias
 * predefinidas de los enemigos al arrancar, cuando heroes puede no estar levantado—; lo que las acciones cuestan o
 * hacen lo sigue diciendo heroes, que es su dueno.
 *
 * <p>No es una tabla nueva: es la de {@link Caracteristicas} (los mismos nombres y el mismo orden que la red de IA
 * ve como vocabulario, comprobados contra {@code contracts/esquemas/catalogo-oficial.yaml} por
 * {@code ia/pruebas/test_tabla7_oficial.py} y {@code Tabla7LocalTest}), vista por prototipo: cada uno tiene tres
 * acciones seguidas, en el orden en que se desbloquean (RC-01: niveles 1, 4 y 8).
 */
public final class Tabla7Local {

    /** RC-01: el nivel en que se desbloquea la primera, la segunda y la tercera accion de cada prototipo. */
    public static final List<Integer> NIVELES_DE_DESBLOQUEO = List.of(1, 4, 8);

    /** Los ocho prototipos de la Tabla 7, en el orden de la tabla. */
    public static final List<String> PROTOTIPOS = Caracteristicas.PROTOTIPOS;

    private static final int ACCIONES_POR_PROTOTIPO = NIVELES_DE_DESBLOQUEO.size();

    private Tabla7Local() {
    }

    /** Las tres acciones del prototipo, con el nombre exacto de la tabla y por orden de desbloqueo; vacia si no lo conoce. */
    public static List<String> accionesDe(String prototipo) {
        int indice = prototipo == null ? -1 : PROTOTIPOS.indexOf(prototipo);
        if (indice < 0) {
            return List.of();
        }
        // La posicion 0 de ACCIONES es el ataque basico; despues van las 24 de la Tabla 7, tres por prototipo.
        int desde = 1 + indice * ACCIONES_POR_PROTOTIPO;
        return Caracteristicas.ACCIONES.subList(desde, desde + ACCIONES_POR_PROTOTIPO);
    }

    /** Las acciones que el prototipo tiene desbloqueadas en ese nivel (1, 4 y 8); vacia si no lo conoce o el nivel es 0. */
    public static List<String> desbloqueadasEn(String prototipo, int nivel) {
        List<String> todas = accionesDe(prototipo);
        int cuantas = (int) NIVELES_DE_DESBLOQUEO.stream().filter(umbral -> nivel >= umbral).count();
        return todas.subList(0, Math.min(cuantas, todas.size()));
    }

    /** El nivel del ultimo desbloqueo alcanzado (1, 4 u 8), o 0 si el nivel no llega al primero. */
    public static int tramoDe(int nivel) {
        int tramo = 0;
        for (int umbral : NIVELES_DE_DESBLOQUEO) {
            if (nivel >= umbral) {
                tramo = umbral;
            }
        }
        return tramo;
    }

    /**
     * El nombre exacto de la tabla para lo que alguien escribio, sin importar tildes, mayusculas ni espacios de los
     * extremos (como lo hace heroes al validar una estrategia); vacio si no es una accion de ese prototipo.
     */
    public static Optional<String> canonica(String prototipo, String nombre) {
        if (nombre == null) {
            return Optional.empty();
        }
        String buscado = normalizar(nombre);
        return accionesDe(prototipo).stream().filter(a -> normalizar(a).equals(buscado)).findFirst();
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
