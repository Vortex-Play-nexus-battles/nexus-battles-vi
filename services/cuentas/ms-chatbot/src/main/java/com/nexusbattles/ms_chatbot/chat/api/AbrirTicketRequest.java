package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// ms-chatbot.yaml 1.3.0: AbrirTicket.
public record AbrirTicketRequest(
    @NotNull Categoria categoria,
    @NotBlank @Size(max = 150) String asunto,
    @NotBlank @Size(max = 2000) String mensaje
) {
}
