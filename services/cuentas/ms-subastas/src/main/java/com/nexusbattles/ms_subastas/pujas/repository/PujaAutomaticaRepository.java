package com.nexusbattles.ms_subastas.pujas.repository;

import com.nexusbattles.ms_subastas.pujas.model.PujaAutomatica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PujaAutomaticaRepository extends JpaRepository<PujaAutomatica, UUID> {

    List<PujaAutomatica> findBySubastaIdAndActivaTrue(UUID subastaId);

    /** Hay como maximo una por jugador y subasta (constraint unica en V1). */
    Optional<PujaAutomatica> findBySubastaIdAndJugadorId(UUID subastaId, UUID jugadorId);
}
