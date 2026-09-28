package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface TerminoProhibidoRepository
        extends JpaRepository<TerminoProhibido, Long>, JpaSpecificationExecutor<TerminoProhibido> {

    /** Busca por la forma normalizada, que es unica (V6). */
    Optional<TerminoProhibido> findByNormalizado(String normalizado);

    /** Lo que carga la verificacion (y lo que se guarda en la cache), en un orden estable. */
    List<TerminoProhibido> findByActivoTrueOrderByNormalizadoAsc();
}
