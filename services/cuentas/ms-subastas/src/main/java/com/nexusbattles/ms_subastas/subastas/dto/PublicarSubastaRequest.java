package com.nexusbattles.ms_subastas.subastas.dto;

import com.nexusbattles.ms_subastas.subastas.model.DuracionSubasta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record PublicarSubastaRequest(
        @NotBlank String elementoInventarioId,
        @NotNull UUID productoId,
        @NotNull DuracionSubasta duracion,
        @NotNull @Positive BigDecimal precioInicial,
        @Positive BigDecimal precioCompraInmediata) {
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rechazarPropiedadDesconocida(String nombre, Object valor) {
        throw new IllegalArgumentException("Propiedad no permitida: " + nombre);
    }
    public void validarPrecios() {
        if (precioCompraInmediata != null && precioCompraInmediata.compareTo(precioInicial) < 0) {
            throw new IllegalArgumentException("El precio de compra inmediata debe ser mayor o igual al precio inicial");
        }
    }
}
