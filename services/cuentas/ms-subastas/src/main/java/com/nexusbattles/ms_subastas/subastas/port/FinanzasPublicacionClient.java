package com.nexusbattles.ms_subastas.subastas.port;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Puerto de los cobros del vendedor en ms-finanzas: la comision de publicar
 * (Tabla 25) y, desde B8, la penalizacion de cancelar (7.7.10). La
 * idempotencia financiera usa el id de subasta: una comision y una
 * penalizacion por subasta como mucho.
 */
public interface FinanzasPublicacionClient {
    void debitarComision(UUID jugadorUid, BigDecimal monto, UUID subastaId, String concepto);
    void compensarDebito(UUID subastaId, String motivo);

    /**
     * Cobra la penalizacion de cancelar (refId {@code sub-cancelacion-{subastaId}}).
     *
     * @throws com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException
     *         con codigo {@code SALDO_INSUFICIENTE} si el vendedor no tiene con que pagarla
     */
    void debitarPenalizacionCancelacion(UUID jugadorUid, BigDecimal monto, UUID subastaId);

    /** Devuelve la penalizacion si la cancelacion no llego a confirmarse. */
    void compensarPenalizacionCancelacion(UUID subastaId, String motivo);
}
