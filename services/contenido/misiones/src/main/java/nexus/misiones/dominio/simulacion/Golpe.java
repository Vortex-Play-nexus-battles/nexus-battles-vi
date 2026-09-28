package nexus.misiones.dominio.simulacion;

/**
 * Lo que el motor dice de un golpe: el dano ya aplicado (0 si no supero la
 * defensa) y si el efecto sorteado fue el critico.
 */
public record Golpe(int dano, boolean critico) {

    public Golpe {
        if (dano < 0) {
            throw new IllegalArgumentException("Un golpe no cura.");
        }
    }
}
