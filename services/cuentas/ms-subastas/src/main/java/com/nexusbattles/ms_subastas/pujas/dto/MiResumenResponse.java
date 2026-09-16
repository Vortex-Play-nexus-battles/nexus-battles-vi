package com.nexusbattles.ms_subastas.pujas.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

/**
 * Lo que el jugador tiene en juego ahora mismo, sumando todas las subastas.
 *
 * <p>Deliberadamente NO incluye saldo total ni disponible: eso lo sabe
 * ms-finanzas, que todavia no existe. Este servicio solo puede responder por lo
 * que el mismo retiene, y decir mas seria inventarlo.
 *
 * @param subastasGanando en cuantas subastas su puja es la oferta vigente. Sale
 *                        de contar sus pujas ACTIVA: el indice unico parcial
 *                        garantiza como mucho una por subasta, y ACTIVA
 *                        significa justamente "es la oferta vigente".
 */
public record MiResumenResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal creditosRetenidos,
        int subastasGanando) {
}
