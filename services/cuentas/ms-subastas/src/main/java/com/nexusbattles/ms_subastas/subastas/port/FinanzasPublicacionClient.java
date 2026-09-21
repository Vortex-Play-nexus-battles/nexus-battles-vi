package com.nexusbattles.ms_subastas.subastas.port;

import java.math.BigDecimal;
import java.util.UUID;

/** Puerto de comisiones de publicacion; la idempotencia financiera usa el id de subasta. */
public interface FinanzasPublicacionClient {
    void debitarComision(UUID jugadorUid, BigDecimal monto, UUID subastaId, String concepto);
    void compensarDebito(UUID subastaId, String motivo);
}
