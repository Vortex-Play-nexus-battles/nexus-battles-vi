package com.nexusbattles.ms_chatbot.chat.repository;

import com.nexusbattles.ms_chatbot.chat.model.Conversacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversacionRepository extends JpaRepository<Conversacion, UUID> {

    // Cada sesion (visitante anonimo o usuario autenticado) tiene, a lo sumo,
    // una Conversacion. Esto es lo que permite el historial persistente del
    // criterio de aceptacion de HU-CHA-001.
    Optional<Conversacion> findByIdentificadorSesion(String identificadorSesion);

    // ms-chatbot.yaml 1.7.0 (7.4.7 «conversaciones iniciadas por periodo»):
    // cuando empezo cada conversacion iniciada en [desde, hasta). fecha_inicio
    // no cambia aunque se borre el historial (indice de V13).
    @Query("""
        select c.fechaInicio from Conversacion c
        where c.fechaInicio >= :desde and c.fechaInicio < :hasta
        """)
    List<Instant> buscarIniciosEntre(@Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
