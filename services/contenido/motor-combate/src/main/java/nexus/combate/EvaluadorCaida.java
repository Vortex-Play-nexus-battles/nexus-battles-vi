package nexus.combate;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.DoubleSupplier;

public final class EvaluadorCaida {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private final DoubleSupplier generador;

    public EvaluadorCaida(DoubleSupplier generador) {
        this.generador = Objects.requireNonNull(generador, "El generador es obligatorio");
    }

    public boolean obtiene(BigDecimal tasaDeCaida) {
        Objects.requireNonNull(tasaDeCaida, "La tasa de caida es obligatoria");
        if (tasaDeCaida.signum() < 0 || tasaDeCaida.compareTo(CIEN) > 0) {
            throw new IllegalArgumentException("La tasa de caida debe estar entre 0 y 100");
        }
        if (tasaDeCaida.signum() == 0) {
            return false;
        }
        if (tasaDeCaida.compareTo(CIEN) == 0) {
            return true;
        }
        double valor = generador.getAsDouble();
        if (valor < 0 || valor >= 1) {
            throw new IllegalStateException("El generador debe producir valores entre 0 y 1");
        }
        return BigDecimal.valueOf(valor)
                .compareTo(tasaDeCaida.movePointLeft(2)) < 0;
    }
}
