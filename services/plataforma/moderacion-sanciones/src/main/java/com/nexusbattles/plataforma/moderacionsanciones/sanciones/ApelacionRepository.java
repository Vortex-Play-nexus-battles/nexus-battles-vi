package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApelacionRepository extends JpaRepository<Apelacion, UUID> {

    Optional<Apelacion> findBySancionIdAndEstado(UUID sancionId, Apelacion.Estado estado);

    List<Apelacion> findByEstadoOrderByCreadaEnAsc(Apelacion.Estado estado);

    List<Apelacion> findByUsuarioIdOrderByCreadaEnDesc(UUID usuarioId);

    /** Las abiertas en un periodo, para las metricas de moderacion (HU-MET-001). */
    List<Apelacion> findByCreadaEnBetween(OffsetDateTime desde, OffsetDateTime hasta);
}
