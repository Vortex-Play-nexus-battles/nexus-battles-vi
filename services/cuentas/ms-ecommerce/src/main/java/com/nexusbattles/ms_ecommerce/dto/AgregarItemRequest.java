package com.nexusbattles.ms_ecommerce.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AgregarItemRequest {

    /**
     * El identificador del producto en el catalogo maestro (UUID del servicio
     * productos, RF-CAR-010), ya no el BIGINT de la tabla local.
     *
     * <p>Un numero JSON ({@code "productoId": 1}) se sigue aceptando: Jackson lo
     * convierte a texto, asi que un cliente viejo no recibe un 400 de forma
     * sino la respuesta de verdad — que ese producto no existe en el catalogo.
     */
    @NotBlank(message = "El id del producto es obligatorio")
    private String productoId;

    @NotNull
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private Integer cantidad;
}
