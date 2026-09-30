package com.nexusbattles.ms_subastas.pujas.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexusbattles.ms_subastas.notificaciones.Textos;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.model.TipoPuja;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Una linea del historial de pujas de una subasta.
 *
 * @param esTuya para que la interfaz pueda resaltar las propias sin tener que
 *               comparar identificadores ella misma, y sobre todo sin tener que
 *               conocer el uid del jugador que mira.
 * @param postor B8: el apodo del postor al pujar, anonimizado parcialmente
 *               («Usuario que realizo cada puja (anonimizado parcialmente)»,
 *               7.7.9). Nunca el uid: el historial es publico. Nulo en las
 *               pujas anteriores a B8, que no guardaban el apodo.
 */
public record PujaDelHistorialResponse(
        UUID id,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal monto,
        TipoPuja tipo,
        EstadoPuja estado,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant creadaEn,
        boolean esTuya,
        String postor) {

    public static PujaDelHistorialResponse de(Puja puja, UUID quienMira) {
        return new PujaDelHistorialResponse(
                puja.getId(), puja.getMonto(), puja.getTipo(), puja.getEstado(), puja.getCreadaEn(),
                quienMira != null && quienMira.equals(puja.getJugadorId()),
                puja.getApodoPostor() == null ? null : Textos.anonimizar(puja.getApodoPostor()));
    }
}
