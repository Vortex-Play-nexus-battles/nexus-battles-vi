package com.nexusbattles.plataforma.torneos.torneo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EquipoRepository extends JpaRepository<Equipo, UUID> {

    List<Equipo> findByTorneoIdOrderByCreadoEnAsc(UUID torneoId);

    long countByTorneoIdAndInscritoTrue(UUID torneoId);
}
