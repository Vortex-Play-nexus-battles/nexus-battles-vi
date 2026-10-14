package com.nexusbattles.ms_chatbot.chat.consultas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

// ms-subastas-listado.yaml 1.2.0: SubastaResumen (GET /api/v1/subastas,
// publico). Solo lo que el chat usa (1.16.0, 7.4.4): nada del vendedor.
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubastaActivaDto(
    String nombreProducto,
    String tipoProducto,
    BigDecimal ofertaVigente,
    BigDecimal precioCompraInmediata,
    Integer cantidadPujas,
    Instant fechaFin,
    String miniaturaUrl
) {
}
