package com.nexusbattles.ms_chatbot.chat.consultas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

// creditos.yaml: GET /creditos/{uid}/movimientos (el propio jugador, con su token).
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaginaMovimientosDto(List<Movimiento> content, long totalElements) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Movimiento(BigDecimal monto, String concepto, String signo) {
    }
}
