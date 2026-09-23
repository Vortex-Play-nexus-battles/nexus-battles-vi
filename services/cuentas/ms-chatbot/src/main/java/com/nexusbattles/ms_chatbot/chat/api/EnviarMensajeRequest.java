package com.nexusbattles.ms_chatbot.chat.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnviarMensajeRequest(
    @NotBlank @Size(max = 4000) String contenido,
    String adjuntoUrl
) {
}
