package com.nexusbattles.ms_subastas.subastas.dto;

import com.nexusbattles.ms_subastas.subastas.model.DuracionSubasta;
import com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException;
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

    /**
     * 7.7.2: «El precio de compra inmediata debe ser superior al precio minimo
     * de puja». Superior, no igual: hasta B8 se aceptaba igual, y una compra
     * inmediata al precio minimo deja sin sentido la subasta por pujas.
     */
    public void validarPrecios() {
        if (precioCompraInmediata != null && precioCompraInmediata.compareTo(precioInicial) <= 0) {
            throw new PublicacionSubastaException(PublicacionSubastaException.Motivo.REGLA_NEGOCIO,
                    PublicacionSubastaException.COMPRA_INMEDIATA_NO_SUPERIOR,
                    "El precio de compra inmediata debe ser superior al precio minimo de puja");
        }
    }
}
