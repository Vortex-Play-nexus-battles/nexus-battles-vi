package com.nexusbattles.ms_subastas.pujas.creditos;

import java.math.BigDecimal;
import java.util.UUID;

public record ReservaCredito(UUID id, UUID jugadorId, BigDecimal monto, EstadoReserva estado) {

    public enum EstadoReserva {
        RESERVADA,
        LIBERADA,
        CONSUMIDA
    }
}
