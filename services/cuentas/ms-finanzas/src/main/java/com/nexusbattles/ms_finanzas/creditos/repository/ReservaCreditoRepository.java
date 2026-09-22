package com.nexusbattles.ms_finanzas.creditos.repository;

import com.nexusbattles.ms_finanzas.creditos.domain.ReservaCredito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservaCreditoRepository extends JpaRepository<ReservaCredito, UUID> {

    Optional<ReservaCredito> findByIdempotencyKey(String idempotencyKey);

    /**
     * Movimientos de credito de un jugador, del mas reciente al mas antiguo.
     *
     * <p>Toda operacion sobre el saldo —reserva de apuesta, cobro, devolucion,
     * recompensa por jugar, inscripcion a un torneo— deja una fila en esta
     * tabla: es la misma que da idempotencia a `acreditar` y `debitar`. Por eso
     * el historial del jugador (HU-PAG-002, #569) se lee de aqui y no de
     * `transacciones`, que solo guarda pagos en moneda real.
     */
    Page<ReservaCredito> findByJugadorUidOrderByCreadoDesc(String jugadorUid, Pageable pagina);
}
