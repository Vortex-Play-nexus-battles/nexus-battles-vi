package com.nexusbattles.ms_subastas.pujas.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
 *
 *               <p>No se expone el apodo de los demas postores: vive en
 *               ms-identidad y traerlo obligaria a este servicio a consultar
 *               otro dominio para pintar una lista. La interfaz muestra "otro
 *               jugador", que es lo que de verdad sabemos.
 */
public record PujaDelHistorialResponse(
        UUID id,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal monto,
        TipoPuja tipo,
        EstadoPuja estado,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant creadaEn,
        boolean esTuya) {

    public static PujaDelHistorialResponse de(Puja puja, UUID quienMira) {
        return new PujaDelHistorialResponse(
                puja.getId(), puja.getMonto(), puja.getTipo(), puja.getEstado(), puja.getCreadaEn(),
                quienMira != null && quienMira.equals(puja.getJugadorId()));
    }
}
