package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.model.Calificacion;

import java.time.Instant;
import java.util.UUID;

public record CalificacionResponse(
    UUID id,
    UUID mensajeId,
    boolean util,
    String comentario,
    Instant fechaCalificacion
) {
    public static CalificacionResponse desde(Calificacion calificacion) {
        return new CalificacionResponse(
            calificacion.getId(),
            calificacion.getMensaje().getId(),
            calificacion.isUtil(),
            calificacion.getComentario(),
            calificacion.getFechaCalificacion()
        );
    }
}
