package com.nexusbattles.ms_chatbot.chat.conocimiento;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

// HU-CHA-012 (RF-CHA-014): la candidata rinde peor que produccion. Sale como
// 409 en formato problem details (regla 4), con los dos resultados completos
// para que el panel muestre que casos fallaron.
public class DespliegueRechazadoException extends ErrorResponseException {

    private final transient ComparacionEvaluacion comparacion;

    public DespliegueRechazadoException(ComparacionEvaluacion comparacion) {
        super(HttpStatus.CONFLICT, crearDetalle(comparacion), null);
        this.comparacion = comparacion;
    }

    public ComparacionEvaluacion getComparacion() {
        return comparacion;
    }

    private static ProblemDetail crearDetalle(ComparacionEvaluacion comparacion) {
        ResultadoEvaluacion candidata = comparacion.candidata();
        ResultadoEvaluacion produccion = comparacion.produccion();
        ProblemDetail detalle = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "La version candidata acierta " + candidata.aciertos() + " de " + candidata.casosEvaluados()
                + " casos y la de produccion " + produccion.aciertos() + " de " + produccion.casosEvaluados()
                + ". No se despliega una version que rinde peor.");
        detalle.setTitle("Candidata con peor desempeno");
        detalle.setProperty("candidata", candidata);
        detalle.setProperty("produccion", produccion);
        return detalle;
    }
}
