package com.nexusbattles.ms_ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TarjetaDTO(
    @NotBlank(message = "El titular es obligatorio")
    @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "Nombre inválido")
    String titular,

    @NotBlank(message = "El número es obligatorio")
    @Pattern(regexp = "^\\d{16}$", message = "La tarjeta debe tener 16 dígitos")
    String numero,

    @NotBlank(message = "La fecha es obligatoria")
    @Pattern(regexp = "^(0[1-9]|1[0-2])/\\d{2}$", message = "Formato debe ser MM/YY")
    String fechaExpiracion,

    @NotBlank(message = "El CVV es obligatorio")
    @Pattern(regexp = "^\\d{3,4}$", message = "CVV debe tener 3 o 4 dígitos")
    String cvv
) {}
