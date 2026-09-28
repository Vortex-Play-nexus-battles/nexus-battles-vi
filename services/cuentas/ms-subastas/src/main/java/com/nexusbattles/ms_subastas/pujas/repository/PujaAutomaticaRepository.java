package com.nexusbattles.ms_subastas.pujas.repository;

import com.nexusbattles.ms_subastas.pujas.model.PujaAutomatica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PujaAutomaticaRepository extends JpaRepository<PujaAutomatica, UUID> {

    List<PujaAutomatica> findBySubastaIdAndActivaTrue(UUID subastaId);

    /** Hay como maximo una por jugador y subasta (constraint unica en V1). */
    Optional<PujaAutomatica> findBySubastaIdAndJugadorId(UUID subastaId, UUID jugadorId);

    /** Las suyas en cualquier subasta: participar sin haber pujado todavia (7.7.9, «Mis pujas»). */
    List<PujaAutomatica> findByJugadorId(UUID jugadorId);

    /** Quien tiene una automatica ACTIVA en la subasta: tambien participa (avisos de 7.7.8). */
    @Query("select pa.jugadorId from PujaAutomatica pa where pa.subastaId = :subastaId and pa.activa = true")
    List<UUID> jugadoresConAutomatica(@Param("subastaId") UUID subastaId);

    /**
     * Al cerrarse o cancelarse la subasta, sus automaticas dejan de tener
     * sentido. La consulta del motor ya las ignoraba por el estado de la
     * subasta; desactivarlas deja la fila diciendo la verdad para «Mis pujas».
     */
    @Modifying
    @Transactional
    @Query("update PujaAutomatica pa set pa.activa = false where pa.subastaId = :subastaId and pa.activa = true")
    int desactivarTodas(@Param("subastaId") UUID subastaId);
}
