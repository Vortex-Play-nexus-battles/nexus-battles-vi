package nexus.combate;

import java.math.BigDecimal;
import java.util.Objects;

public record ProductoBotin(
        String id,
        String nombre,
        TipoBotin tipo,
        ParteArmaduraBotin parteArmadura,
        BigDecimal tasaDeCaida) {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    public ProductoBotin {
        exigirTexto(id, "id");
        exigirTexto(nombre, "nombre");
        Objects.requireNonNull(tipo, "El tipo del producto es obligatorio");
        Objects.requireNonNull(tasaDeCaida, "La tasa de caida es obligatoria");
        if (tasaDeCaida.signum() < 0 || tasaDeCaida.compareTo(CIEN) > 0) {
            throw new IllegalArgumentException("La tasa de caida debe estar entre 0 y 100");
        }
        if (tipo == TipoBotin.ARMADURA && parteArmadura == null) {
            throw new IllegalArgumentException("La armadura debe declarar su parte");
        }
        if (tipo != TipoBotin.ARMADURA && parteArmadura != null) {
            throw new IllegalArgumentException("Solo una armadura puede declarar su parte");
        }
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }
}
