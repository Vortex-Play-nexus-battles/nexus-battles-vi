package com.nexusbattles.ms_chatbot.chat.consultas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

// ms-subastas-pujas.yaml 0.4.0: Participacion (GET /api/v1/mis-pujas, con el
// token del propio jugador). Solo lo que el chat usa (1.16.0, 7.4.4). Los
// importes llegan como texto decimal.
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParticipacionDto(
    String nombreProducto,
    String estadoSubasta,
    String estado,
    String tuMejorPuja,
    String ofertaVigente,
    Integer cantidadPujas,
    Instant fechaFin
) {

    /** Si la subasta sigue abierta: solo de esas se habla. */
    public boolean activa() {
        return "ACTIVA".equals(estadoSubasta);
    }
}
