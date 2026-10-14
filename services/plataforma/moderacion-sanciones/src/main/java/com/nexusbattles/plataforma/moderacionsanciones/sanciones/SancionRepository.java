package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SancionRepository extends JpaRepository<Sancion, UUID> {

    List<Sancion> findByUsuarioIdOrderByEmitidaEnDesc(UUID usuarioId);

    /** Las que pueden restringir: no revertidas y distintas de advertencia. */
    List<Sancion> findByUsuarioIdAndRevertidaEnIsNullAndTipoNot(UUID usuarioId, Sancion.Tipo tipo);

    /** Las emitidas en un periodo, para las metricas de moderacion (HU-MET-001). */
    List<Sancion> findByEmitidaEnBetweenOrderByEmitidaEnAsc(OffsetDateTime desde, OffsetDateTime hasta);

    /** SA-3: cuantas se le han emitido a una cuenta, las revertidas tambien (historial). */
    long countByUsuarioId(UUID usuarioId);

    /** SA-3: la emision mas reciente de una cuenta. */
    Optional<Sancion> findFirstByUsuarioIdOrderByEmitidaEnDesc(UUID usuarioId);

    /** SA-3: las cuentas con alguna sancion, para reconciliar su historial en identidad. */
    @Query("select distinct s.usuarioId from Sancion s")
    List<UUID> usuariosConSanciones();
}
