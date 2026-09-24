package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.model.Mensaje;

import java.time.Instant;
import java.util.UUID;

public record MensajeResponse(
    UUID id,
    String remitente,
    String contenido,
    String adjuntoUrl,
    Instant fechaEnvio
) {
    public static MensajeResponse desde(Mensaje mensaje) {
        return new MensajeResponse(
            mensaje.getId(),
            mensaje.getRemitente().name(),
            mensaje.getContenido(),
            mensaje.getAdjuntoUrl(),
            mensaje.getFechaEnvio()
        );
    }
}
