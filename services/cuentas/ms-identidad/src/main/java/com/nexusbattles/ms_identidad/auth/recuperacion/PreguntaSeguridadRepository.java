package com.nexusbattles.ms_identidad.auth.recuperacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PreguntaSeguridadRepository extends JpaRepository<PreguntaSeguridad, UUID> {

    List<PreguntaSeguridad> findByUsuarioIdOrderByOrdenAsc(Long usuarioId);

    long countByUsuarioId(Long usuarioId);

    /**
     * Borrado en bloque, ejecutado en el acto. No {@code deleteAll(lista)}:
     * Hibernate ordena las inserciones antes que los borrados al vaciar la
     * sesion, y reemplazar las preguntas chocaria con la unicidad
     * (usuario, orden) de V3.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from PreguntaSeguridad p where p.usuarioId = :usuarioId")
    int borrarDe(@Param("usuarioId") Long usuarioId);
}
