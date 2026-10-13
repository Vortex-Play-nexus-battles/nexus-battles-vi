package com.nexusbattles.ms_chatbot.chat.conocimiento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

// ms-chatbot.yaml 1.3.8 (7.4.10 «programar actualizaciones»): cada minuto
// revisa si la candidata tiene una publicacion programada que ya vencio y,
// si es asi, la despliega con la misma evaluacion que el boton del panel.
//
// Con varias instancias del servicio no se publica dos veces: la reclama una
// sola (VersionBaseConocimientoRepository.reclamarDespliegueProgramado).
@Component
public class DespliegueProgramadoTarea {

    static final long CADA_MS = 60_000;

    private static final Logger log = LoggerFactory.getLogger(DespliegueProgramadoTarea.class);

    private final EvaluacionBaseConocimientoService evaluacionService;

    public DespliegueProgramadoTarea(EvaluacionBaseConocimientoService evaluacionService) {
        this.evaluacionService = evaluacionService;
    }

    @Scheduled(fixedDelay = CADA_MS, initialDelay = CADA_MS)
    public void revisar() {
        try {
            evaluacionService.desplegarSiCorresponde(Instant.now()).ifPresent(this::informar);
        } catch (RuntimeException excepcion) {
            // Una falla no debe tumbar la aplicacion ni la siguiente revision.
            log.error("No se pudo revisar la publicacion programada del chatbot.", excepcion);
        }
    }

    private void informar(DespliegueProgramado resultado) {
        if (resultado.desplegada()) {
            log.info("Publicacion programada del chatbot: la version {} ya esta en produccion.", resultado.numero());
        } else {
            log.warn("Publicacion programada del chatbot: la version {} no se desplego. {}",
                resultado.numero(), resultado.motivo());
        }
    }
}
