package com.nexusbattles.ms_chatbot.chat.conocimiento;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

// ms-chatbot.yaml 1.3.8: PUT /chatbot/admin/base-conocimiento/borrador/programacion.
public record ProgramarDespliegueRequest(@NotNull Instant desplegarEn) {
}
