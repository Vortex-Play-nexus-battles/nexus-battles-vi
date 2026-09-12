package com.nexusbattles.ms_subastas.pujas.creditos;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Envuelve al CreditoClient real con cortacircuitos y reintento.
 *
 * Por que hace falta: la reserva de creditos se invoca DENTRO de la
 * transaccion que mantiene el lock pesimista sobre la fila de la subasta. Si
 * ms-finanzas deja de responder y no se corta rapido, cada puja se queda
 * esperando con el lock tomado y se congelan todas las pujas de esa subasta.
 *
 * Los rechazos de negocio (CreditoClientException: saldo insuficiente, reserva
 * ya liberada...) estan excluidos del reintento y del cortacircuitos en la
 * configuracion: no son fallos del servicio y reintentarlos seria incorrecto
 * — liberar dos veces la misma reserva lanza RESERVA_YA_LIBERADA.
 *
 * El reintento de `reservar` solo es seguro porque la operacion es idempotente
 * por Idempotency-Key. Si ms-finanzas no garantiza esa idempotencia, hay que
 * quitar el reintento de este metodo.
 */
@RequiredArgsConstructor
public class CreditoClientResiliente implements CreditoClient {

    private static final String INSTANCIA = "creditos";

    private final CreditoClient delegado;

    @Override
    @CircuitBreaker(name = INSTANCIA)
    @Retry(name = INSTANCIA)
    public ReservaCredito reservar(UUID jugadorId, BigDecimal monto, UUID subastaId, String idempotencyKey) {
        return delegado.reservar(jugadorId, monto, subastaId, idempotencyKey);
    }

    @Override
    @CircuitBreaker(name = INSTANCIA)
    public void liberar(UUID reservaId) {
        delegado.liberar(reservaId);
    }

    @Override
    @CircuitBreaker(name = INSTANCIA)
    public void consumir(UUID reservaId) {
        delegado.consumir(reservaId);
    }

    @Override
    @CircuitBreaker(name = INSTANCIA)
    @Retry(name = INSTANCIA)
    public BigDecimal saldoDisponible(UUID jugadorId) {
        return delegado.saldoDisponible(jugadorId);
    }
}
