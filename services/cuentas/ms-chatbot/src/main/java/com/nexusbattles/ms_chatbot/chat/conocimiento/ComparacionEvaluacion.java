package com.nexusbattles.ms_chatbot.chat.conocimiento;

// HU-CHA-012 (RF-CHA-014): candidata contra produccion, con los MISMOS casos.
// Regla de despliegue: la candidata es apta si no acierta menos que la
// version en produccion.
public record ComparacionEvaluacion(
    ResultadoEvaluacion candidata,
    ResultadoEvaluacion produccion,
    boolean candidataApta
) {

    public static ComparacionEvaluacion de(ResultadoEvaluacion candidata, ResultadoEvaluacion produccion) {
        return new ComparacionEvaluacion(candidata, produccion, candidata.aciertos() >= produccion.aciertos());
    }
}
