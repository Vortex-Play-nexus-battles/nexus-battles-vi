package com.nexusbattles.ms_ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoVitrinaDto {
    private Long id;
    private String nombre;
    private String imagenUrl;
    private String descripcion;
    private String habilidades;
    private String tipo;

    private BigDecimal precioFinal;
    private BigDecimal precioOriginal;
    private String moneda;

    private Boolean enPromocion;
    private Integer porcentajeDescuento;

    private Boolean esPropio;
    private Boolean enListaDeseos;
}