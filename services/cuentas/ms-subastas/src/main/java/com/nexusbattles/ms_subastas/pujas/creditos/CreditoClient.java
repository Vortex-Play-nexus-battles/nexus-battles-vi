package com.nexusbattles.ms_subastas.pujas.creditos;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Contrato hacia ms-finanzas que necesita HU-SUB-004. Borrador a validar con
 * Juan Diego (Dia 1-2): idempotencia, semantica de "disponible" (neto de
 * reservas) y codigos de error distinguibles por CreditoClientException.
 *
 * Mientras el endpoint real no exista, {@link CreditoClientFake} permite
 * avanzar el motor de pujas sin quedar bloqueado.
 */
public interface CreditoClient {

    /**
     * Retiene creditos del ofertante sin debitarlos todavia. Debe ser
     * idempotente: reintentar con la misma {@code idempotencyKey} no debe
     * generar una segunda reserva.
     */
    ReservaCredito reservar(UUID jugadorId, BigDecimal monto, UUID subastaId, String idempotencyKey);

    /** Libera una reserva (el ofertante fue superado, o la subasta cerro sin adjudicacion). */
    void liberar(UUID reservaId);

    /**
     * Convierte la reserva en debito real (la puja gano la subasta) y abona al
     * vendedor.
     *
     * @param vendedorId quien recibe los creditos. Lo abona ms-finanzas dentro
     *                   de esta misma llamada y en su transaccion local, que es
     *                   la unica forma de que cobrar al comprador y pagar al
     *                   vendedor sean atomicos: son dos cuentas de su dominio,
     *                   no del nuestro. Sin este dato el comprador paga y el
     *                   vendedor no cobra.
     */
    void consumir(UUID reservaId, UUID vendedorId);

    /** Saldo disponible del jugador, ya neto de sus reservas activas. */
    BigDecimal saldoDisponible(UUID jugadorId);
}
