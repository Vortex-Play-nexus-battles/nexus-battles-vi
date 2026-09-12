package com.nexusbattles.ms_subastas.subastas.repository;

import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubastaRepository extends JpaRepository<Subasta, UUID> {

    /**
     * Lee la subasta con lock pesimista (SELECT ... FOR UPDATE). Es el guardia
     * real de concurrencia de HU-SUB-004: serializa las pujas sobre la misma
     * subasta entre procesos y replicas, donde un synchronized de la JVM no
     * alcanza. Solo valido dentro de una transaccion.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subasta s where s.id = :id")
    Optional<Subasta> findByIdParaActualizar(@Param("id") UUID id);

    /**
     * Subastas que siguen ACTIVA pero cuyo plazo ya paso. Devuelve solo ids a
     * proposito: cada una se cierra en su propia transaccion, que vuelve a
     * leerla con lock, de modo que una subasta problematica no arrastre al resto.
     */
    @Query("select s.id from Subasta s where s.estado = 'ACTIVA' and s.fechaFin <= :ahora")
    List<UUID> findIdsDeActivasVencidas(@Param("ahora") Instant ahora);

    /**
     * Subastas activas con al menos una puja automatica lista para reaccionar:
     * activa, de un jugador que no es ya el mejor postor. Es la entrada del
     * motor de pujas automaticas.
     */
    @Query("""
            select distinct s.id from Subasta s, PujaAutomatica pa
            where pa.subastaId = s.id
              and pa.activa = true
              and s.estado = 'ACTIVA'
              and s.fechaFin > :ahora
              and (s.mejorPostorId is null or s.mejorPostorId <> pa.jugadorId)
            """)
    List<UUID> findIdsConPujaAutomaticaPendiente(@Param("ahora") Instant ahora);
}
