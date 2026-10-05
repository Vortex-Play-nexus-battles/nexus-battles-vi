package com.nexusbattles.ms_ecommerce.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code PUT /carrito/items/{itemId}/cantidad}: {@code {"cantidad": 3}}.
 *
 * <p>El rango 1..20 no lo comprueba la validacion de Spring sino el servicio:
 * asi el rechazo sale con su propio {@code type}
 * ({@code cantidad-fuera-de-rango}) y la interfaz no tiene que distinguirlo de
 * un cuerpo ilegible.
 */
public record CambiarCantidadRequest(@NotNull(message = "La cantidad es obligatoria") Integer cantidad) {
}
