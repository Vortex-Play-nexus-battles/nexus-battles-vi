package com.nexusbattles.plataforma.salaspartidas.configuracion;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.CerrarAbandonadas;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Programa el cierre de salas y partidas abandonadas (auditoria de DEV del
 * 30-sep). La logica vive en {@link CerrarAbandonadas}, que se prueba sin
 * Spring; esta clase solo decide <i>cuando</i>: cada
 * {@code salas.abandono.cada-ms} (10 min por omision), con el primer disparo
 * tras el mismo intervalo para no consultar la base antes de terminar de
 * arrancar. Cerrar dos veces lo mismo no hace dano: la sala y la partida se
 * guardan con bloqueo optimista y la apuesta se libera de forma idempotente.
 */
@Configuration
@EnableScheduling
class CierreDeAbandonadasProgramado {

    private final CerrarAbandonadas cerrar;

    CierreDeAbandonadasProgramado(CerrarAbandonadas cerrar) {
        this.cerrar = cerrar;
    }

    @Scheduled(fixedDelayString = "${salas.abandono.cada-ms:600000}",
               initialDelayString = "${salas.abandono.cada-ms:600000}")
    void cerrarAbandonadas() {
        cerrar.ejecutar();
    }
}
