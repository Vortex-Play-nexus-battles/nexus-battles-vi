package com.nexusbattles.ms_subastas.pujas.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

/**
 * Lo que el jugador que mira necesita saber de su propia situacion en una
 * subasta. Todo sale de la base de datos de este servicio.
 *
 * <p>Existe porque el listado de HU-SUB-011 es el mismo para todos: no puede
 * decir si <b>tu</b> vas ganando, cuanto llevas retenido o que limite
 * automatico tienes puesto. Sin esto la pantalla tenia que inventarselo, y lo
 * que hacia era mostrar siempre "no vas ganando" y "sin puja automatica",
 * aunque fuera falso.
 *
 * @param creditosRetenidos suma de las pujas propias que siguen siendo oferta
 *                          vigente. Es lo unico que este servicio sabe de
 *                          creditos: el saldo total y el disponible los tiene
 *                          ms-finanzas. Hasta B8 salia como
 *                          {@code retenidoAqui}, un nombre que el contrato
 *                          nunca declaro ({@code ms-subastas-pujas.yaml} exige
 *                          {@code creditosRetenidos}).
 * @param segundosParaVolverAPujar cuanto falta para poder pujar otra vez en
 *                          ESTA subasta. Cero si ya se puede.
 * @param siguiendo         si esta en su lista de seguimiento (B8, 7.7.9).
 */
public record MiParticipacionResponse(
        boolean vasGanando,
        boolean teSuperaron,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal tuOfertaVigente,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal creditosRetenidos,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal limiteAutomatico,
        boolean automaticaActiva,
        long segundosParaVolverAPujar,
        boolean siguiendo) {
}
