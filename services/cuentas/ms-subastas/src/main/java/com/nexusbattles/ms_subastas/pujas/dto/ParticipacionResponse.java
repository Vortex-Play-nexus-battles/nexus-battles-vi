package com.nexusbattles.ms_subastas.pujas.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Una subasta en «Mis pujas» (7.7.9, B8): {@code Participacion} de
 * {@code ms-subastas-pujas.yaml} 0.4.0.
 */
public record ParticipacionResponse(
        UUID subastaId,
        String nombreProducto,
        String miniaturaUrl,
        String estadoSubasta,
        String estado,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal tuMejorPuja,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal ofertaVigente,
        int cantidadPujas,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant fechaFin,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant ultimaPujaEn) {

    /**
     * @param tuMejorPuja  nula si solo configuro una puja automatica que todavia no emitio
     * @param ultimaPujaEn nula en el mismo caso
     */
    public static ParticipacionResponse de(Subasta subasta, UUID jugadorId, BigDecimal tuMejorPuja,
                                           Instant ultimaPujaEn) {
        return new ParticipacionResponse(subasta.getId(), subasta.getNombreProducto(), subasta.getMiniaturaUrl(),
                subasta.getEstado().name(), estadoDe(subasta, jugadorId, tuMejorPuja != null), tuMejorPuja,
                subasta.getOfertaVigente(), subasta.getCantidadPujas(), subasta.getFechaFin(), ultimaPujaEn);
    }

    /** GANANDO, SUPERADA, GANADA, PERDIDA, CERRADA o AUTOMATICA; ver el contrato. */
    static String estadoDe(Subasta subasta, UUID jugadorId, boolean pujo) {
        boolean esElMejor = jugadorId.equals(subasta.getMejorPostorId());
        return switch (subasta.getEstado()) {
            case ACTIVA -> !pujo ? "AUTOMATICA" : esElMejor ? "GANANDO" : "SUPERADA";
            case ADJUDICADA -> esElMejor ? "GANADA" : "PERDIDA";
            case SIN_ADJUDICACION, CANCELADA -> "CERRADA";
        };
    }
}
