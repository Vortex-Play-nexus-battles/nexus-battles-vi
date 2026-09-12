package com.nexusbattles.ms_subastas.pujas.creditos;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Doble de prueba de CreditoClient para desarrollar el motor de pujas sin
 * depender del endpoint real de ms-finanzas (Juan Diego, Dia 1-2 del Sprint
 * 2). Saldo y reservas en memoria, pensado para tests y para correr el
 * servicio en local mientras no exista el cliente HTTP real.
 */
public class CreditoClientFake implements CreditoClient {

    private final Map<UUID, BigDecimal> saldos = new ConcurrentHashMap<>();
    private final Map<UUID, ReservaCredito> reservas = new ConcurrentHashMap<>();
    private final Map<String, UUID> reservasPorIdempotencyKey = new ConcurrentHashMap<>();

    public void acreditar(UUID jugadorId, BigDecimal monto) {
        saldos.merge(jugadorId, monto, BigDecimal::add);
    }

    @Override
    public synchronized ReservaCredito reservar(UUID jugadorId, BigDecimal monto, UUID subastaId, String idempotencyKey) {
        UUID reservaExistente = reservasPorIdempotencyKey.get(idempotencyKey);
        if (reservaExistente != null) {
            return reservas.get(reservaExistente);
        }

        BigDecimal disponible = saldoDisponible(jugadorId);
        if (disponible.compareTo(monto) < 0) {
            throw new CreditoClientException(CreditoClientException.Motivo.SALDO_INSUFICIENTE,
                    "El jugador " + jugadorId + " no tiene saldo suficiente para reservar " + monto);
        }

        ReservaCredito reserva = new ReservaCredito(UUID.randomUUID(), jugadorId, monto, ReservaCredito.EstadoReserva.RESERVADA);
        reservas.put(reserva.id(), reserva);
        reservasPorIdempotencyKey.put(idempotencyKey, reserva.id());
        return reserva;
    }

    @Override
    public synchronized void liberar(UUID reservaId) {
        ReservaCredito reserva = obtenerReserva(reservaId);
        if (reserva.estado() == ReservaCredito.EstadoReserva.LIBERADA) {
            throw new CreditoClientException(CreditoClientException.Motivo.RESERVA_YA_LIBERADA,
                    "La reserva " + reservaId + " ya estaba liberada");
        }
        if (reserva.estado() == ReservaCredito.EstadoReserva.CONSUMIDA) {
            throw new CreditoClientException(CreditoClientException.Motivo.RESERVA_YA_CONSUMIDA,
                    "La reserva " + reservaId + " ya fue consumida, no se puede liberar");
        }
        reservas.put(reservaId, new ReservaCredito(reserva.id(), reserva.jugadorId(), reserva.monto(), ReservaCredito.EstadoReserva.LIBERADA));
    }

    @Override
    public synchronized void consumir(UUID reservaId) {
        ReservaCredito reserva = obtenerReserva(reservaId);
        if (reserva.estado() != ReservaCredito.EstadoReserva.RESERVADA) {
            throw new CreditoClientException(CreditoClientException.Motivo.RESERVA_YA_CONSUMIDA,
                    "La reserva " + reservaId + " no esta en estado RESERVADA");
        }
        saldos.merge(reserva.jugadorId(), reserva.monto(), BigDecimal::subtract);
        reservas.put(reservaId, new ReservaCredito(reserva.id(), reserva.jugadorId(), reserva.monto(), ReservaCredito.EstadoReserva.CONSUMIDA));
    }

    @Override
    public synchronized BigDecimal saldoDisponible(UUID jugadorId) {
        BigDecimal saldo = saldos.getOrDefault(jugadorId, BigDecimal.ZERO);
        BigDecimal reservado = reservas.values().stream()
                .filter(r -> r.jugadorId().equals(jugadorId) && r.estado() == ReservaCredito.EstadoReserva.RESERVADA)
                .map(ReservaCredito::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return saldo.subtract(reservado);
    }

    private ReservaCredito obtenerReserva(UUID reservaId) {
        ReservaCredito reserva = reservas.get(reservaId);
        if (reserva == null) {
            throw new CreditoClientException(CreditoClientException.Motivo.RESERVA_INEXISTENTE,
                    "No existe la reserva " + reservaId);
        }
        return reserva;
    }
}
