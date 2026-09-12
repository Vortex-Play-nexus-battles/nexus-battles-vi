package com.nexusbattles.ms_subastas.pujas.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PujarRequest(
        @NotNull(message = "el monto de la puja es obligatorio")
        @DecimalMin(value = "0.01", message = "el monto de la puja debe ser positivo")
        BigDecimal monto) {
}
