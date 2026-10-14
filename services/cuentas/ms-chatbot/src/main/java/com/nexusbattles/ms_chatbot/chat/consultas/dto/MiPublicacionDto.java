package com.nexusbattles.ms_chatbot.chat.consultas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

// ms-subastas-panel.yaml 1.0.0: MiPublicacion (GET
// /api/v1/mis-subastas/publicadas?estado=ACTIVA, con el token del propio
// jugador). Solo lo que el chat usa (1.16.0, 7.4.4).
@JsonIgnoreProperties(ignoreUnknown = true)
public record MiPublicacionDto(
    String nombreProducto,
    String estado,
    String ofertaVigente,
    Integer cantidadPujas,
    Instant fechaFin
) {
}
