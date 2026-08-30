package nexus.combate;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Conserva la secuencia de participantes de un combate.
 *
 * <p>HU-JUE-001 exige que el orden se sortee una sola vez al iniciar la
 * partida y permanezca invariable hasta su finalizacion. La seleccion usa el
 * algoritmo Fisher-Yates, que asigna la misma probabilidad a cada
 * permutacion cuando el generador subyacente es uniforme.</p>
 */
public final class ColaTurnos {

    private static final int MINIMO_PARTICIPANTES = 2;

    private final List<String> secuencia;
    private int posicionActiva;

    private ColaTurnos(List<String> secuencia) {
        this.secuencia = List.copyOf(secuencia);
        this.posicionActiva = 0;
    }

    /**
     * Sortea el orden inicial con un generador criptograficamente fuerte.
     *
     * @param participantes identificadores unicos de quienes entran al combate
     * @return cola ubicada en el primer turno del orden sorteado
     */
    public static ColaTurnos sortear(List<String> participantes) {
        return sortear(participantes, new SecureRandom());
    }

    /** Variante con generador inyectado para obtener pruebas reproducibles. */
    static ColaTurnos sortear(
            List<String> participantes,
            RandomGenerator generador) {
        validar(participantes);
        Objects.requireNonNull(generador, "El generador aleatorio es obligatorio");

        List<String> orden = new ArrayList<>(participantes);
        for (int indice = orden.size() - 1; indice > 0; indice--) {
            int intercambio = generador.nextInt(indice + 1);
            String temporal = orden.get(indice);
            orden.set(indice, orden.get(intercambio));
            orden.set(intercambio, temporal);
        }
        return new ColaTurnos(orden);
    }

    /** Devuelve el participante cuyo turno esta activo. */
    public String participanteActivo() {
        return secuencia.get(posicionActiva);
    }

    /**
     * Avanza al siguiente participante y vuelve al primero al terminar una
     * ronda. La secuencia original nunca se reordena.
     */
    public void avanzar() {
        posicionActiva = (posicionActiva + 1) % secuencia.size();
    }

    /** Devuelve una vista inmutable del orden sorteado. */
    public List<String> secuencia() {
        return secuencia;
    }

    private static void validar(List<String> participantes) {
        if (participantes == null
                || participantes.size() < MINIMO_PARTICIPANTES) {
            throw new IllegalArgumentException(
                    "El combate requiere al menos dos participantes");
        }
        if (participantes.stream().anyMatch(
                participante -> participante == null || participante.isBlank())) {
            throw new IllegalArgumentException(
                    "Cada participante debe tener un identificador valido");
        }
        if (new HashSet<>(participantes).size() != participantes.size()) {
            throw new IllegalArgumentException(
                    "Los identificadores de participantes no se pueden repetir");
        }
    }
}
