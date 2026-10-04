package nexus.combate.reglas;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Una formula de la Tabla 6 como datos: {@code base + NdM}.
 *
 * <p>«10 + 1d6» es base 10 y un dado de seis caras; «1d4» es base 0. El nivel
 * multiplica la base y no los dados (ejemplo del documento: «Un mago de fuego
 * de nivel 3 posee un ataque base de 30 puntos»); ese escalado lo hace el
 * catalogo de heroes o el inventario, aqui llega ya hecho.
 *
 * @param base          parte fija
 * @param cantidadDados cuantos dados
 * @param caras         caras de cada dado
 */
public record Formula(int base, int cantidadDados, int caras) {

    public Formula {
        if (base < 0 || cantidadDados < 0 || caras < 0) {
            throw new IllegalArgumentException("Una formula no admite valores negativos.");
        }
        if (cantidadDados > 0 && caras < 1) {
            throw new IllegalArgumentException("Un dado necesita al menos una cara.");
        }
    }

    /** Tira los dados y suma la base. */
    public int tirar(RandomGenerator azar) {
        Objects.requireNonNull(azar, "Sin generador no hay tirada.");
        int total = base;
        for (int i = 0; i < cantidadDados; i++) {
            total += azar.nextInt(caras) + 1;
        }
        return total;
    }
}
