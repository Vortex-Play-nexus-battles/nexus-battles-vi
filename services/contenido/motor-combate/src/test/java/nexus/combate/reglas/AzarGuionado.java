package nexus.combate.reglas;

import nexus.combate.IndiceNormal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.random.RandomGenerator;

/**
 * Un generador con las tiradas escritas de antemano, para comprobar una regla
 * con numeros exactos. Cada {@code nextInt(caras)} consume el siguiente entero
 * del guion (un dado de seis que saca un 4 se guiona como 3: {@code nextInt}
 * devuelve de 0 a caras-1). Cada {@code nextGaussian} consume la siguiente
 * fila del guion, ya convertida a la z que la produce.
 *
 * <p>Si una regla pide mas azar del guionado, la prueba falla con un mensaje
 * claro: una tirada de mas es un defecto, no un detalle.
 */
final class AzarGuionado implements RandomGenerator {

    private final Deque<Integer> enteros = new ArrayDeque<>();
    private final Deque<Double> gaussianas = new ArrayDeque<>();

    /** Resultados de dados, como caras sacadas (1..N): se guardan como nextInt = cara - 1. */
    AzarGuionado dados(int... caras) {
        for (int cara : caras) {
            enteros.add(cara - 1);
        }
        return this;
    }

    /** Resultados crudos de nextInt (p. ej. el critico: 0 = 120 %, 60 = 180 %). */
    AzarGuionado enteros(int... valores) {
        for (int valor : valores) {
            enteros.add(valor);
        }
        return this;
    }

    /** Filas de la tabla de 8.000 que saldran, en orden, con el indice por omision. */
    AzarGuionado filas(int... filas) {
        IndiceNormal indice = IndiceNormal.porOmision();
        for (int fila : filas) {
            gaussianas.add((fila - indice.media()) / indice.desviacion());
        }
        return this;
    }

    boolean agotado() {
        return enteros.isEmpty() && gaussianas.isEmpty();
    }

    @Override
    public int nextInt(int limite) {
        Integer siguiente = enteros.poll();
        if (siguiente == null) {
            throw new AssertionError("La regla pidio un entero (limite " + limite + ") que no estaba en el guion");
        }
        if (siguiente < 0 || siguiente >= limite) {
            throw new AssertionError("El guion trae " + siguiente + " para un limite de " + limite);
        }
        return siguiente;
    }

    @Override
    public double nextGaussian() {
        Double siguiente = gaussianas.poll();
        if (siguiente == null) {
            throw new AssertionError("La regla pidio una fila de la tabla que no estaba en el guion");
        }
        return siguiente;
    }

    @Override
    public long nextLong() {
        throw new AssertionError("La regla pidio un long: no esta guionado");
    }
}
