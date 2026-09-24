package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.conocimiento.ResultadoEvaluacion.FalloDeCaso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// HU-CHA-012 (RF-CHA-014): evaluacion periodica de la version en produccion
// para detectar desempeno degradado. Todos los dias a las 3:00 (hora de
// Colombia, fuera de la hora de mas uso).
//
// Supuesto (la historia no dice que hacer ante la degradacion): solo se
// registra una advertencia en la bitacora con las preguntas que fallan; NO
// se revierte sola. Revertir es una decision del administrador.
@Component
public class VigilanciaBaseConocimientoTarea {

    static final String CRON = "0 0 3 * * *";
    static final String ZONA = "America/Bogota";

    private static final Logger log = LoggerFactory.getLogger(VigilanciaBaseConocimientoTarea.class);

    private final EvaluacionBaseConocimientoService evaluacionService;

    public VigilanciaBaseConocimientoTarea(EvaluacionBaseConocimientoService evaluacionService) {
        this.evaluacionService = evaluacionService;
    }

    @Scheduled(cron = CRON, zone = ZONA)
    public void evaluarProduccion() {
        try {
            evaluacionService.vigilarProduccion().ifPresentOrElse(
                this::informar,
                () -> log.info("Evaluacion periodica del chatbot omitida: no hay casos de evaluacion activos."));
        } catch (RuntimeException excepcion) {
            // Una falla aqui no debe tumbar la aplicacion ni la siguiente
            // ejecucion; solo se registra con su causa.
            log.error("No se pudo evaluar la version en produccion del chatbot.", excepcion);
        }
    }

    private void informar(VigilanciaProduccion vigilancia) {
        ResultadoEvaluacion evaluacion = vigilancia.evaluacion();
        if (vigilancia.degradada()) {
            log.warn("Desempeno degradado del chatbot: la version {} acierta {} de {} casos (tasa {}; antes {}). "
                    + "Preguntas que falla: {}",
                evaluacion.numero(), evaluacion.aciertos(), evaluacion.casosEvaluados(),
                evaluacion.tasaAcierto(), vigilancia.tasaAnterior(),
                evaluacion.fallos().stream().map(FalloDeCaso::pregunta).toList());
        } else {
            log.info("Evaluacion periodica del chatbot: la version {} acierta {} de {} casos (tasa {}).",
                evaluacion.numero(), evaluacion.aciertos(), evaluacion.casosEvaluados(), evaluacion.tasaAcierto());
        }
    }
}
