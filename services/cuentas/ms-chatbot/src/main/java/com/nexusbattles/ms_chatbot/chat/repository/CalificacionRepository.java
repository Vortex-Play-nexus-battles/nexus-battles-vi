package com.nexusbattles.ms_chatbot.chat.repository;

import com.nexusbattles.ms_chatbot.chat.model.Calificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CalificacionRepository extends JpaRepository<Calificacion, UUID> {

    // Soporta el criterio "no se admite calificar dos veces la misma
    // respuesta" de HU-CHA-011 (el indice unico de V3 es el respaldo final).
    boolean existsByMensajeId(UUID mensajeId);
}
