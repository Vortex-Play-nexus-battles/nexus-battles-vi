package com.nexusbattles.ms_chatbot.chat.repository;

import com.nexusbattles.ms_chatbot.chat.model.Conversacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConversacionRepository extends JpaRepository<Conversacion, UUID> {

    // Cada sesion (visitante anonimo o usuario autenticado) tiene, a lo sumo,
    // una Conversacion. Esto es lo que permite el historial persistente del
    // criterio de aceptacion de HU-CHA-001.
    Optional<Conversacion> findByIdentificadorSesion(String identificadorSesion);
}
