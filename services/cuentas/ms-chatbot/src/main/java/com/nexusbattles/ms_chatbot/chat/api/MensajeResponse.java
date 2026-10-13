package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.enriquecido.RespuestaEnriquecida;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;

import java.time.Instant;
import java.util.UUID;

// ms-chatbot.yaml: Mensaje. `adjuntoUrl` esta obsoleto desde 1.3.0 (solo lo
// traen mensajes viejos); `enriquecido` es de 1.3.4 y solo lo traen las
// respuestas del bot que tienen algo que pintar.
public record MensajeResponse(
    UUID id,
    String remitente,
    String contenido,
    String adjuntoUrl,
    Instant fechaEnvio,
    RespuestaEnriquecida enriquecido
) {
    public static MensajeResponse desde(Mensaje mensaje) {
        return new MensajeResponse(
            mensaje.getId(),
            mensaje.getRemitente().name(),
            mensaje.getContenido(),
            mensaje.getAdjuntoUrl(),
            mensaje.getFechaEnvio(),
            mensaje.getEnriquecido()
        );
    }
}
