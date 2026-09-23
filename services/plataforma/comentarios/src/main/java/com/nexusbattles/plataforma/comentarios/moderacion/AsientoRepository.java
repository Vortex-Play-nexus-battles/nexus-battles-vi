package com.nexusbattles.plataforma.comentarios.moderacion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** El historico de decisiones de moderacion (RF-COM-008). */
public interface AsientoRepository extends JpaRepository<AsientoDeModeracion, String> {

    /** De la mas antigua a la mas reciente: un historico se lee en orden. */
    List<AsientoDeModeracion> findByComentarioIdOrderByFechaAsc(String comentarioId);
}
