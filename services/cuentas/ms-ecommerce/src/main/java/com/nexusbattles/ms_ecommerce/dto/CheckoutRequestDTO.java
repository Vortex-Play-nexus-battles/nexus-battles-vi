package com.nexusbattles.ms_ecommerce.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequestDTO(
    @NotNull(message = "El ID del carrito es obligatorio")
    Long carritoId,

    @Valid
    @NotNull(message = "Los datos de la tarjeta son obligatorios")
    TarjetaDTO tarjeta
) {}
