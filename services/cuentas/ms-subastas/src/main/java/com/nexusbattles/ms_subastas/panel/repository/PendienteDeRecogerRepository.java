package com.nexusbattles.ms_subastas.panel.repository;

import com.nexusbattles.ms_subastas.panel.model.EstadoPendiente;
import com.nexusbattles.ms_subastas.panel.model.PendienteDeRecoger;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendienteDeRecogerRepository extends JpaRepository<PendienteDeRecoger, UUID> {

    /** Los pendientes de un jugador, el que vence antes primero. */
    List<PendienteDeRecoger> findByGanadorIdAndEstadoOrderByVenceEnAsc(UUID ganadorId, EstadoPendiente estado);

    /**
     * El pendiente de esa subasta, con candado: recogerlo a mano y el vencimiento
     * programado no pueden resolverlo los dos a la vez.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PendienteDeRecoger p where p.subastaId = :subastaId")
    Optional<PendienteDeRecoger> findByIdParaActualizar(@Param("subastaId") UUID subastaId);

    /** Solo ids: cada vencido se resuelve en su propia transaccion. */
    @Query("select p.subastaId from PendienteDeRecoger p where p.estado = :estado and p.venceEn <= :ahora")
    List<UUID> idsVencidos(@Param("estado") EstadoPendiente estado, @Param("ahora") Instant ahora);
}
