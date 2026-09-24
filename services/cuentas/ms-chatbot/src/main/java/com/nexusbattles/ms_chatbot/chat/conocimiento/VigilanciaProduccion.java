package com.nexusbattles.ms_chatbot.chat.conocimiento;

// HU-CHA-012 (RF-CHA-014): resultado de la evaluacion periodica de la version
// en produccion, comparada con su evaluacion anterior.
//   tasaAnterior  null si la version nunca se habia evaluado.
//   degradada     true si ahora acierta una proporcion menor que antes.
public record VigilanciaProduccion(
    ResultadoEvaluacion evaluacion,
    Double tasaAnterior,
    boolean degradada
) {

    public static VigilanciaProduccion de(ResultadoEvaluacion evaluacion, Double tasaAnterior) {
        boolean degradada = tasaAnterior != null
            && evaluacion.tasaAcierto() != null
            && evaluacion.tasaAcierto() < tasaAnterior;
        return new VigilanciaProduccion(evaluacion, tasaAnterior, degradada);
    }
}
