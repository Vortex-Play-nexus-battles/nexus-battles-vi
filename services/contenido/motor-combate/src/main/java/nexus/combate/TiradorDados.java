package nexus.combate;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class TiradorDados {

    private TiradorDados() {
    }

    public static int resolver(DetalleAtaque detalle, RandomGenerator generador) {
        Objects.requireNonNull(detalle, "El detalle de ataque es obligatorio");
        Objects.requireNonNull(generador, "El generador aleatorio es obligatorio");

        int total = detalle.base();
        for (int i = 0; i < detalle.cantidadDados(); i++) {
            total += generador.nextInt(detalle.caras()) + 1;
        }
        return total;
    }

    public static RandomGenerator generadorProduccion() {
        return new SecureRandom();
    }
}
