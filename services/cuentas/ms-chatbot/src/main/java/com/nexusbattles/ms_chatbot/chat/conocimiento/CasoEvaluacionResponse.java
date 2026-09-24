package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.motor.model.CasoEvaluacion;

import java.time.Instant;
import java.util.UUID;

public record CasoEvaluacionResponse(
    UUID id,
    String pregunta,
    String temaClaveEsperada,
    boolean activo,
    Instant fechaCreacion
) {

    public static CasoEvaluacionResponse desde(CasoEvaluacion caso) {
        return new CasoEvaluacionResponse(
            caso.getId(),
            caso.getPregunta(),
            caso.getTemaClaveEsperada(),
            caso.isActivo(),
            caso.getFechaCreacion());
    }
}
