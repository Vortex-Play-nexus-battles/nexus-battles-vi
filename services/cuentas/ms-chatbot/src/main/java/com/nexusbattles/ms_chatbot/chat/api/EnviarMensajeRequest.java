package com.nexusbattles.ms_chatbot.chat.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnviarMensajeRequest(
    @NotBlank @Size(max = 4000) String contenido,
    // Obsoleto desde ms-chatbot.yaml 1.3.0: el cliente decidio que el chatbot
    // no recibe imagenes. Se acepta (y se sigue validando su largo) para no
    // romper a quien lo mande, pero ChatController lo ignora y no se guarda.
    @Deprecated @Size(max = 500) String adjuntoUrl
) {
}
