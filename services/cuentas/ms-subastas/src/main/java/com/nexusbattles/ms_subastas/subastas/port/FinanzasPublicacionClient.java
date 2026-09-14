package com.nexusbattles.ms_subastas.subastas.port;

import java.math.BigDecimal;
import java.util.UUID;

/** Puerto pendiente del contrato HTTP real de ms-finanzas. */
public interface FinanzasPublicacionClient {
    void debitarComision(UUID jugadorId, BigDecimal monto, UUID subastaId, String idempotencyKey);
    void compensarDebito(UUID jugadorId, BigDecimal monto, UUID subastaId, String idempotencyKey);
}
