package com.nexusbattles.ms_chatbot.chat.consultas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// ms-subastas-listado.yaml 1.2.0: PaginaDeSubastas (GET /api/v1/subastas).
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaginaDeSubastasDto(List<SubastaActivaDto> contenido, long totalElementos) {

    /** Las de esta pagina, nunca null. */
    public List<SubastaActivaDto> subastas() {
        return contenido == null ? List.of() : contenido;
    }
}
