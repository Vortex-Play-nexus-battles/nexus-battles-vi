package com.nexusbattles.ms_ecommerce.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AgregarItemRequest {
    @NotNull(message = "El id del producto es obligatorio")
    private Long productoId;

    @NotNull
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private Integer cantidad;
}