package com.nexusbattles.ms_subastas.pujas.creditos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(CreditoClientFake.class);

    private final Map<UUID, BigDecimal> saldos = new ConcurrentHashMap<>();
    private final Map<UUID, ReservaCredito> reservas = new ConcurrentHashMap<>();
    private final Map<String, UUID> reservasPorIdempotencyKey = new ConcurrentHashMap<>();

    /**
     * Saldo con el que aparece un jugador del que no se sabe nada. En las
     * pruebas es cero y cada una acredita lo que necesita, que es lo correcto:
     * una prueba de saldo insuficiente no puede depender de un regalo.
     *
     * <p>Levantando el servicio en local, en cambio, cero significa que
     * <b>nadie puede pujar</b>: ms-finanzas no existe todavia y no hay ningun
     * sitio desde donde acreditar. Con este valor por encima de cero la
     * funcionalidad se puede ver funcionar de extremo a extremo.
     */
    private final BigDecimal saldoInicial;

    /**
     * Si una reserva que este doble no conoce se trata como ya liberada.
     *
     * <p><b>Por que hace falta.</b> Las reservas viven en memoria y las pujas
     * viven en PostgreSQL. Al reiniciar el servicio, la base de datos sigue
     * teniendo pujas que apuntan a reservas que este doble ya no tiene, asi
     * que la siguiente puja sobre esa subasta intenta liberar una reserva
     * inexistente y la peticion muere con un 500. Reiniciar el servicio a
     * mitad de desarrollo es lo normal, asi que sin esto la funcionalidad
     * queda inservible en cuanto se reinicia una vez.
     *
     * <p>Queda en false para las pruebas: alli el doble arranca y muere con
     * cada caso, no hay estado heredado, y una reserva desconocida si es un
     * error de verdad que no se debe tapar. ms-finanzas, cuando exista,
     * persiste sus reservas y este problema no se le plantea.
     */
    private final boolean tolerarReservasDeOtraEjecucion;

    public CreditoClientFake() {
        this(BigDecimal.ZERO, false);
    }

    public CreditoClientFake(BigDecimal saldoInicial) {
        this(saldoInicial, false);
    }

    public CreditoClientFake(BigDecimal saldoInicial, boolean tolerarReservasDeOtraEjecucion) {
        this.saldoInicial = saldoInicial == null ? BigDecimal.ZERO : saldoInicial;
        this.tolerarReservasDeOtraEjecucion = tolerarReservasDeOtraEjecucion;
    }

    public void acreditar(UUID jugadorId, BigDecimal monto) {
        ajustarSaldo(jugadorId, monto);
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
        if (tolerarReservasDeOtraEjecucion && !reservas.containsKey(reservaId)) {
            // Puja escrita antes del ultimo reinicio: su reserva se perdio con
            // la memoria del proceso anterior. Darla por liberada es lo unico
            // coherente, porque ya no retiene nada.
            log.warn("La reserva {} no existe en este doble: se da por liberada. "
                    + "Viene de una ejecucion anterior, porque las reservas del doble no sobreviven "
                    + "a un reinicio y las pujas si.", reservaId);
            return;
        }
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
    public synchronized void consumir(UUID reservaId, UUID vendedorId) {
        ReservaCredito reserva = obtenerReserva(reservaId);
        if (reserva.estado() != ReservaCredito.EstadoReserva.RESERVADA) {
            throw new CreditoClientException(CreditoClientException.Motivo.RESERVA_YA_CONSUMIDA,
                    "La reserva " + reservaId + " no esta en estado RESERVADA");
        }
        ajustarSaldo(reserva.jugadorId(), reserva.monto().negate());
        // El doble abona al vendedor igual que hace ms-finanzas: si no, las
        // pruebas darian por bueno un flujo en el que el comprador paga y el
        // vendedor no cobra.
        if (vendedorId != null) {
            ajustarSaldo(vendedorId, reserva.monto());
        }
        reservas.put(reservaId, new ReservaCredito(reserva.id(), reserva.jugadorId(), reserva.monto(), ReservaCredito.EstadoReserva.CONSUMIDA));
    }

    @Override
    public synchronized BigDecimal saldoDisponible(UUID jugadorId) {
        BigDecimal saldo = saldoDe(jugadorId);
        BigDecimal reservado = reservas.values().stream()
                .filter(r -> r.jugadorId().equals(jugadorId) && r.estado() == ReservaCredito.EstadoReserva.RESERVADA)
                .map(ReservaCredito::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return saldo.subtract(reservado);
    }

    /**
     * Suma (o resta, con monto negativo) partiendo del saldo inicial cuando el
     * jugador no se habia visto antes.
     *
     * <p>Con {@code Map.merge} no salia bien: sobre una clave ausente coloca el
     * valor tal cual en vez de combinarlo, asi que el saldo inicial se perdia.
     * Un vendedor que nunca habia interactuado pasaba de sus creditos de
     * partida a solo lo que acababa de cobrar.
     */
    private void ajustarSaldo(UUID jugadorId, BigDecimal delta) {
        saldos.put(jugadorId, saldoDe(jugadorId).add(delta));
    }

    private BigDecimal saldoDe(UUID jugadorId) {
        return saldos.computeIfAbsent(jugadorId, quien -> saldoInicial);
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
