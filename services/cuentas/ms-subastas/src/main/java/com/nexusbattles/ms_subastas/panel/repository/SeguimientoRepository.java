package com.nexusbattles.ms_subastas.panel.repository;

import com.nexusbattles.ms_subastas.panel.model.Seguimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SeguimientoRepository extends JpaRepository<Seguimiento, Seguimiento.Clave> {

    /** Quien sigue una subasta: los destinatarios de «cambios en estas subastas». */
    @Query("select s.jugadorId from Seguimiento s where s.subastaId = :subastaId")
    List<UUID> seguidoresDe(@Param("subastaId") UUID subastaId);

    List<Seguimiento> findByJugadorIdOrderByCreadoEnDesc(UUID jugadorId);

    boolean existsBySubastaIdAndJugadorId(UUID subastaId, UUID jugadorId);

    /**
     * Seguir es idempotente: dos PUT seguidos (o dos pestañas a la vez) dejan
     * una sola fila. ON CONFLICT y no un save que choque, porque en
     * PostgreSQL el choque abortaria la transaccion.
     */
    @Modifying
    @Transactional
    @Query(value = """
            insert into seguimientos (subasta_id, jugador_id, creado_en)
            values (:subastaId, :jugadorId, :ahora)
            on conflict (subasta_id, jugador_id) do nothing
            """, nativeQuery = true)
    int seguir(@Param("subastaId") UUID subastaId, @Param("jugadorId") UUID jugadorId, @Param("ahora") Instant ahora);

    @Modifying
    @Transactional
    @Query("delete from Seguimiento s where s.subastaId = :subastaId and s.jugadorId = :jugadorId")
    int dejarDeSeguir(@Param("subastaId") UUID subastaId, @Param("jugadorId") UUID jugadorId);
}
