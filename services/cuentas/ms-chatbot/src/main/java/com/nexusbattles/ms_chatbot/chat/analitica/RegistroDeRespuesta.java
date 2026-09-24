package com.nexusbattles.ms_chatbot.chat.analitica;

import java.time.Instant;

// HU-CHA-012: una respuesta del bot, reducida a lo que las analiticas usan.
// Solo existen para respuestas generadas desde V4 (antes no se median).
public record RegistroDeRespuesta(Instant fechaEnvio, Boolean escalado, Integer tiempoRespuestaMs) {
}
