package com.nexusbattles.ms_chatbot.chat.consultas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

// torneos.yaml: TorneoResumen (GET /torneos, publico). Solo lo que el chat usa.
@JsonIgnoreProperties(ignoreUnknown = true)
public record TorneoResumenDto(UUID id, String nombre, String estado, int costoInscripcion, long equiposInscritos,
                               int cupos) {
}
