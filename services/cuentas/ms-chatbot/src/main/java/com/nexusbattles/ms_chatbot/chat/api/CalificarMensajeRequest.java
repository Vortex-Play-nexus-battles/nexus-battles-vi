package com.nexusbattles.ms_chatbot.chat.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// HU-CHA-011: cuerpo de POST /chat/mensajes/{mensajeId}/calificacion.
// 'util' es Boolean (no boolean) a proposito: con el primitivo, un JSON que
// olvide el campo llegaria como false y se guardaria en silencio como una
// calificacion "no util" que el usuario nunca hizo. Con @NotNull, ese caso
// responde 400.
public record CalificarMensajeRequest(
    @NotNull Boolean util,
    @Size(max = 1000) String comentario
) {
}
