package com.nexusbattles.ms_chatbot.chat.repository;

import com.nexusbattles.ms_chatbot.chat.model.Calificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CalificacionRepository extends JpaRepository<Calificacion, UUID> {

    // Soporta el criterio "no se admite calificar dos veces la misma
    // respuesta" de HU-CHA-011 (el indice unico de V3 es el respaldo final).
    boolean existsByMensajeId(UUID mensajeId);

    // HU-CHA-012: satisfaccion del periodo = proporcion de "util" entre las
    // calificaciones hechas en [desde, hasta).
    @Query("""
        select c.util from Calificacion c
        where c.fechaCalificacion >= :desde and c.fechaCalificacion < :hasta
        """)
    List<Boolean> buscarUtilidadEntre(@Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
