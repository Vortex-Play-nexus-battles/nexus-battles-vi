package com.nexusbattles.ms_subastas.pujas.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la compra inmediata. Existe para una sola cosa: obligar al cliente
 * a declarar la confirmacion explicita que pide HU-SUB-004.
 *
 * @param confirmado {@code Boolean} y no {@code boolean} a proposito. Con el
 *                   primitivo, un cuerpo que omitiera el campo se deserializaria
 *                   como {@code false} y el servidor no podria distinguir "el
 *                   jugador no confirmo" de "la peticion venia mal armada".
 *                   Ausente da 400; presente en false da 422 con motivo
 *                   CONFIRMACION_REQUERIDA.
 */
public record CompraInmediataRequest(
        @NotNull(message = "hay que confirmar la compra de forma explicita")
        Boolean confirmado) {

    public boolean estaConfirmada() {
        return Boolean.TRUE.equals(confirmado);
    }
}
