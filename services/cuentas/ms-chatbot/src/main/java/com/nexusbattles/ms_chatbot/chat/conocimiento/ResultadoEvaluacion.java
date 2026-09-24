package com.nexusbattles.ms_chatbot.chat.conocimiento;

import java.util.List;
import java.util.UUID;

// HU-CHA-012 (RF-CHA-014): como le fue a una version con los casos de
// evaluacion. 'fallos' dice exactamente que casos no acerto, para que el
// administrador sepa que corregir antes de volver a intentar el despliegue.
public record ResultadoEvaluacion(
    UUID versionId,
    int numero,
    int casosEvaluados,
    int aciertos,
    Double tasaAcierto,
    List<FalloDeCaso> fallos
) {

    // temaClaveEsperada null = se esperaba que escalara.
    // temaClaveObtenido null = el motor escalo.
    public record FalloDeCaso(
        UUID casoId,
        String pregunta,
        String temaClaveEsperada,
        String temaClaveObtenido
    ) {
    }
}
