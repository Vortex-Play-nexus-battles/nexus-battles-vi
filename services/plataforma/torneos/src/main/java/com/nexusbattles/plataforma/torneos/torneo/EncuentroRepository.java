package com.nexusbattles.plataforma.torneos.torneo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EncuentroRepository extends JpaRepository<Encuentro, UUID> {

    List<Encuentro> findByTorneoIdOrderByNumeroAsc(UUID torneoId);
}
