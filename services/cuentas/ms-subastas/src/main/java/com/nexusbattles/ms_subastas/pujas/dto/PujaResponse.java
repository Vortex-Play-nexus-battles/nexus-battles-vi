package com.nexusbattles.ms_subastas.pujas.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Corresponde al schema PujaResponse de
 * {@code contracts/openapi/ms-subastas-pujas.yaml}.
 *
 * @param monto    como cadena, igual que en el contrato: un decimal en JSON pasa
 *                 por el double de JavaScript, y ahi 0.1 + 0.2 deja de ser 0.3.
 *                 Con creditos de por medio no se negocia.
 * @param creadaEn como cadena ISO-8601, tambien por contrato. El
 *                 {@code ObjectMapper} propio del servicio (JacksonConfig) no
 *                 desactiva WRITE_DATES_AS_TIMESTAMPS, asi que sin esta
 *                 anotacion saldria como un numero epoch.
 */
public record PujaResponse(
        UUID id,
        UUID subastaId,
        UUID jugadorId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal monto,
        EstadoPuja estado,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant creadaEn) {

    public static PujaResponse de(Puja puja) {
        return new PujaResponse(puja.getId(), puja.getSubastaId(), puja.getJugadorId(),
                puja.getMonto(), puja.getEstado(), puja.getCreadaEn());
    }
}
