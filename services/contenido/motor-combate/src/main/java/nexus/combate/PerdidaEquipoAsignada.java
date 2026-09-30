package nexus.combate;

import java.math.BigDecimal;

public record PerdidaEquipoAsignada(
        String combatienteDerrotadoId,
        String elementoId,
        String productoId,
        String propietarioGanadorId,
        BigDecimal tasaDeCaida) {
}
