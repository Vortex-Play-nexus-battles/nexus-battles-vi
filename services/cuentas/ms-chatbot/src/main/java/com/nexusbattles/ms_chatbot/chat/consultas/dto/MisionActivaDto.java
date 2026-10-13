package com.nexusbattles.ms_chatbot.chat.consultas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

// misiones.yaml 1.0.0: MisionActiva (GET /api/v1/misiones/en-curso, con el
// token del propio jugador). Solo lo que el chat usa.
@JsonIgnoreProperties(ignoreUnknown = true)
public record MisionActivaDto(String nombre, String categoria, Heroe heroe, Instant terminaEn, Double progreso) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Heroe(String nombre, Integer nivel) {
    }
}
