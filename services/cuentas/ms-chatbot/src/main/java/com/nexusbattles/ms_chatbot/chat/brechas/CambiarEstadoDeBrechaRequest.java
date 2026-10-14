package com.nexusbattles.ms_chatbot.chat.brechas;

import com.nexusbattles.ms_chatbot.chat.model.EstadoBrecha;
import jakarta.validation.constraints.NotNull;

// ms-chatbot.yaml 1.7.0: CambiarEstadoDeBrecha. Solo el estado: quien lo
// cambia sale del token, nunca del cuerpo.
public record CambiarEstadoDeBrechaRequest(@NotNull EstadoBrecha estado) {
}
