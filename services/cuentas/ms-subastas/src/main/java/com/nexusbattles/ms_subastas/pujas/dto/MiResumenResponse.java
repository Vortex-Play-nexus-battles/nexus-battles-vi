package com.nexusbattles.ms_subastas.pujas.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

/**
 * Lo que el jugador tiene en juego ahora mismo, sumando todas las subastas.
 *
 * @param saldoDisponible lo que le queda libre segun ms-finanzas, ya neto de
 *                        reservas. <b>Puede venir nulo, y ese nulo significa
 *                        "no se sabe", no "cero"</b>: si ms-finanzas no
 *                        responde, la pantalla debe decir que lo desconoce en
 *                        vez de ensenar un numero. Un cero inventado le diria
 *                        al jugador que esta arruinado.
 * @param subastasGanando en cuantas subastas su puja es la oferta vigente. Sale
 *                        de contar sus pujas ACTIVA: el indice unico parcial
 *                        garantiza como mucho una por subasta, y ACTIVA
 *                        significa justamente "es la oferta vigente".
 */
public record MiResumenResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal creditosRetenidos,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoDisponible,
        int subastasGanando) {
}
