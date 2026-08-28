package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TerminoProhibidoRepository extends JpaRepository<TerminoProhibido, Long> {

    boolean existsByTerminoIgnoreCase(String termino);

    Optional<TerminoProhibido> findByTerminoIgnoreCase(String termino);
}
