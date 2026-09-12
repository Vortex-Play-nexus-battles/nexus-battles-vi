package com.nexusbattles.ms_subastas.pujas.dto;

import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PujaResponse(
        UUID id,
        UUID subastaId,
        UUID jugadorId,
        BigDecimal monto,
        EstadoPuja estado,
        Instant creadaEn) {

    public static PujaResponse de(Puja puja) {
        return new PujaResponse(puja.getId(), puja.getSubastaId(), puja.getJugadorId(),
                puja.getMonto(), puja.getEstado(), puja.getCreadaEn());
    }
}
