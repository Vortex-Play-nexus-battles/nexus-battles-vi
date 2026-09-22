package com.nexusbattles.ms_subastas.pujas.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexusbattles.ms_subastas.pujas.model.PujaAutomatica;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param limite se serializa como cadena, tal y como lo declara el contrato: un
 *               decimal en JSON pasa por el double de JavaScript, y ahi
 *               0.1 + 0.2 deja de ser 0.3. Con creditos de por medio no se
 *               negocia.
 */
public record PujaAutomaticaResponse(
        UUID id,
        UUID subastaId,
        UUID jugadorId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal limite,
        boolean activa) {

    public static PujaAutomaticaResponse de(PujaAutomatica automatica) {
        return new PujaAutomaticaResponse(automatica.getId(), automatica.getSubastaId(),
                automatica.getJugadorId(), automatica.getLimite(), automatica.isActiva());
    }
}
