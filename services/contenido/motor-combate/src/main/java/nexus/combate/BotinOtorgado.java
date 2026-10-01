package nexus.combate;

import java.math.BigDecimal;
import java.util.Objects;

public record BotinOtorgado(
        String productoId,
        String nombre,
        TipoBotin tipo,
        OrigenBotin origen,
        BigDecimal tasaDeCaida) {

    public BotinOtorgado {
        if (productoId == null || productoId.isBlank()) {
            throw new IllegalArgumentException("productoId es obligatorio");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("nombre es obligatorio");
        }
        Objects.requireNonNull(tipo, "El tipo es obligatorio");
        Objects.requireNonNull(origen, "El origen es obligatorio");
        Objects.requireNonNull(tasaDeCaida, "La tasa de caida es obligatoria");
    }
}
