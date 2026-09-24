package com.nexusbattles.ms_chatbot.chat.conocimiento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// HU-CHA-012 (RF-CHA-014): crear o editar un caso de evaluacion.
//   temaClaveEsperada  vacio o null = el motor deberia ESCALAR esa pregunta.
//   activo             null se toma como true.
public record CasoEvaluacionRequest(
    @NotBlank @Size(max = 1000) String pregunta,
    @Size(max = 80) String temaClaveEsperada,
    Boolean activo
) {
}
