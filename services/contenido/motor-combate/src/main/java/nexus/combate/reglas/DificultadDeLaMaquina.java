package nexus.combate.reglas;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Cuanto se esfuerza la maquina al decidir — decision D-41 (auditoria del
 * 4-oct, cambio autorizado n.º 4).
 *
 * <p>Las tres juegan con las MISMAS reglas que un humano (§6.1.3): ninguna
 * hace trampa, ninguna ve las tiradas de la partida y ninguna puede elegir una
 * jugada ilegal. Lo que cambia es cuanto ensaya antes de decidir y si se
 * permite descuidos:
 * <ul>
 *   <li>{@link #FACIL} — pocos ensayos y, a veces, una de sus tres mejores
 *       jugadas en lugar de la mejor: se le puede ganar.</li>
 *   <li>{@link #NORMAL} — la jugada con mejor puntaje esperado.</li>
 *   <li>{@link #DIFICIL} — mas ensayos y, ademas, anticipa el golpe que le
 *       devolveran: valora defenderse, debilitar al rival que mas pega y
 *       rematar.</li>
 * </ul>
 *
 * <p>No es una IA entrenada: es un evaluador de jugadas por reglas. El
 * «aprendizaje profundo» del §7.6 sigue fuera de este bloque (D-B7-13).
 *
 * @param muestras         ensayos de cada jugada candidata
 * @param anticipa         si resta la respuesta esperada de los rivales
 * @param descuido         probabilidad de no jugar la mejor
 * @param entreLasMejores  entre cuantas de las mejores elige cuando se descuida
 */
public enum DificultadDeLaMaquina {

    FACIL(4, false, 0.40, 3),
    NORMAL(12, false, 0.0, 1),
    DIFICIL(24, true, 0.0, 1);

    private final int muestras;
    private final boolean anticipa;
    private final double descuido;
    private final int entreLasMejores;

    DificultadDeLaMaquina(int muestras, boolean anticipa, double descuido, int entreLasMejores) {
        this.muestras = muestras;
        this.anticipa = anticipa;
        this.descuido = descuido;
        this.entreLasMejores = entreLasMejores;
    }

    public int muestras() {
        return muestras;
    }

    public boolean anticipa() {
        return anticipa;
    }

    public double descuido() {
        return descuido;
    }

    public int entreLasMejores() {
        return entreLasMejores;
    }

    /**
     * La dificultad escrita en la configuracion ({@code MOTOR_IA_DIFICULTAD}):
     * admite tildes y mayusculas («Fácil», «dificil»). Vacia, {@link #NORMAL}.
     *
     * @throws IllegalArgumentException si no es ninguna de las tres: una
     *                                  dificultad mal escrita se nota al
     *                                  arrancar, no en mitad de una partida
     */
    public static DificultadDeLaMaquina desde(String texto) {
        if (texto == null || texto.isBlank()) {
            return NORMAL;
        }
        String limpio = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        for (DificultadDeLaMaquina d : values()) {
            if (d.name().equals(limpio)) {
                return d;
            }
        }
        throw new IllegalArgumentException(
                "Dificultad de la IA desconocida: «" + texto + "». Vale FACIL, NORMAL o DIFICIL.");
    }
}
