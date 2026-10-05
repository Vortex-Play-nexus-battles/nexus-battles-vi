package nexus.semilla;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Saca de los textos de las reglas del curso los numeros que el dominio pide
 * como campo propio. Las reglas escriben "+1 al ataque" o "3%"; el alta de un
 * producto exige {@code poderDeAtaque}, {@code defensa} y {@code tasaDeCaida}.
 *
 * <p>Solo se lee lo que el texto dice de forma explicita. "-1 al ataque del
 * oponente" no es una bonificacion propia; "+1% de critico al ataque" es un
 * porcentaje de critico, no ataque. Si el texto no trae el numero, se devuelve
 * vacio y quien llama decide, a la vista.
 */
public final class NumerosDeLaRegla {

    private static final Pattern AL_ATAQUE =
            Pattern.compile("(?<![\\d%])\\+(\\d+) al ataque(?! del oponente)");

    private static final Pattern A_LA_DEFENSA =
            Pattern.compile("(?<![\\d%])\\+(\\d+) a la defensa(?! del oponente)");

    private static final Pattern PORCENTAJE =
            Pattern.compile("^\\s*(\\d+(?:[.,]\\d+)?)\\s*%\\s*$");

    private NumerosDeLaRegla() {
    }

    /** El N de "+N al ataque", si la regla lo trae. */
    public static Optional<Integer> bonificacionAlAtaque(String efectos) {
        return primero(AL_ATAQUE, efectos);
    }

    /** El N de "+N a la defensa", si la regla lo trae. */
    public static Optional<Integer> bonificacionALaDefensa(String efectos) {
        return primero(A_LA_DEFENSA, efectos);
    }

    /**
     * "3%" -> 3; "0.04%" -> 0.04. El dominio guarda la tasa de caida como
     * porcentaje entre 0 y 100, igual que la escribe la regla.
     *
     * @throws IllegalArgumentException si el texto no es un porcentaje
     */
    public static BigDecimal porcentaje(String texto) {
        if (texto == null) {
            throw new IllegalArgumentException("La regla no trae porcentaje");
        }
        Matcher coincidencia = PORCENTAJE.matcher(texto);
        if (!coincidencia.matches()) {
            throw new IllegalArgumentException("No es un porcentaje: " + texto);
        }
        return new BigDecimal(coincidencia.group(1).replace(',', '.'));
    }

    private static Optional<Integer> primero(Pattern patron, String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        Matcher coincidencia = patron.matcher(texto);
        return coincidencia.find()
                ? Optional.of(Integer.valueOf(coincidencia.group(1)))
                : Optional.empty();
    }
}
