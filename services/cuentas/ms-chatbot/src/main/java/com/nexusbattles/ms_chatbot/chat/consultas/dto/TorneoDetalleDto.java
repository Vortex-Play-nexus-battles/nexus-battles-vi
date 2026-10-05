package com.nexusbattles.ms_chatbot.chat.consultas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

// torneos.yaml: Torneo (GET /torneos/{id}, publico). Solo lo que el chat usa.
@JsonIgnoreProperties(ignoreUnknown = true)
public record TorneoDetalleDto(UUID id, String nombre, String estado, UUID campeonEquipoId, List<Equipo> equipos,
                               List<Encuentro> encuentros) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Equipo(UUID id, String nombre, boolean ia, List<UUID> integrantes, boolean inscrito,
                         Integer posicion, boolean eliminado) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Encuentro(int numero, String estado, UUID equipoA, UUID equipoB) {
    }
}
