package com.nexusbattles.plataforma.salaspartidas.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Acceso generado por Spring Data. El puerto del dominio lo envuelve. */
interface PartidasSpringData extends JpaRepository<PartidaEntidad, UUID> {

    Optional<PartidaEntidad> findByIdSala(UUID idSala);
}
