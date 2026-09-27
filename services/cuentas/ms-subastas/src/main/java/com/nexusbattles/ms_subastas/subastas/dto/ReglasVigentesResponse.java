package com.nexusbattles.ms_subastas.subastas.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexusbattles.ms_subastas.reglas.ReglasDelDocumento;
import com.nexusbattles.ms_subastas.reglas.ReglasVigentes;
import com.nexusbattles.ms_subastas.subastas.model.DuracionSubasta;
import com.nexusbattles.ms_subastas.subastas.service.CalculadorComisionPublicacion;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * {@code ReglasVigentes} de {@code ms-subastas-listado.yaml} 1.1.0: lo que la
 * interfaz necesita para validar y explicar antes de enviar, dicho por el
 * servidor (B8). Hasta aqui {@code pujas.js} y {@code publicar-subasta.js}
 * tenian las reglas escritas a mano —un incremento de 50 que el servicio no
 * aplicaba, las comisiones 1/3 duplicadas—.
 */
public record ReglasVigentesResponse(
        List<Duracion> duraciones,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal incrementoMinimo,
        boolean incrementoMinimoConfigurado,
        int maxSubastasActivasPorJugador,
        int maxPujasActivasPorJugador,
        int intervaloMinimoSegundos,
        int penalizacionCancelacionPorcentaje,
        int cancelacionProhibidaUltimasHoras,
        int recordatorioMinutosAntesDelCierre,
        int diasParaRecoger,
        String alVencerPendientes) {

    /** Una duracion de la Tabla 25, con su comision. */
    public record Duracion(String codigo, int horas, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal comision) {
    }

    public static ReglasVigentesResponse desde(ReglasVigentes vigentes, CalculadorComisionPublicacion comisiones) {
        List<Duracion> duraciones = Arrays.stream(DuracionSubasta.values())
                .map(d -> new Duracion(d.valorJson(), (int) d.duracion().toHours(), comisiones.calcular(d, false)))
                .toList();
        return new ReglasVigentesResponse(duraciones, vigentes.incrementoMinimo(), vigentes.incremento().isPresent(),
                vigentes.maxSubastasActivasPorJugador(), vigentes.maxPujasActivasPorJugador(),
                vigentes.intervaloMinimoSegundos(), ReglasDelDocumento.PENALIZACION_CANCELACION_PORCENTAJE,
                (int) ReglasDelDocumento.CANCELACION_PROHIBIDA_ULTIMAS.toHours(),
                (int) ReglasDelDocumento.RECORDATORIO_ANTES_DEL_CIERRE.toMinutes(),
                (int) ReglasDelDocumento.PLAZO_PARA_RECOGER.toDays(), vigentes.alVencerPendientes().name());
    }
}
