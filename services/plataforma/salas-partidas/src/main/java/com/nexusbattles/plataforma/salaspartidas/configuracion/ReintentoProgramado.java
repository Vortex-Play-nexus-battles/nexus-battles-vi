package com.nexusbattles.plataforma.salaspartidas.configuracion;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.ReintentarLiquidaciones;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Programa el reintento de las liquidaciones pendientes — HU-JUE-014, CA-06.
 *
 * <p>La cadencia es configurable ({@code salas.apuestas.reintento-ms}, por
 * defecto un minuto) y el primer disparo espera lo mismo, para no salir a
 * consultar la base antes de que el servicio haya terminado de arrancar. La
 * logica vive en {@link ReintentarLiquidaciones}, que se prueba sin Spring;
 * esta clase solo decide <i>cuando</i>.
 */
@Configuration
@EnableScheduling
class ReintentoProgramado {

    private final ReintentarLiquidaciones reintentar;

    ReintentoProgramado(ReintentarLiquidaciones reintentar) {
        this.reintentar = reintentar;
    }

    @Scheduled(fixedDelayString = "${salas.apuestas.reintento-ms:60000}",
               initialDelayString = "${salas.apuestas.reintento-ms:60000}")
    void reintentarLiquidacionesPendientes() {
        reintentar.ejecutar();
    }
}
