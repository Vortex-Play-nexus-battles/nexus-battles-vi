package com.nexusbattles.ms_chatbot.chat.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

// productos.yaml: un elemento de GET /api/v1/productos en la proyeccion
// publica (sin token). Solo lo que el chat usa: nada de campos internos.
//
// 1.13.0: `imagen`, la referencia de la imagen del producto tal como la
// guarda el catalogo; antes de ir en una tarjeta pasa por ImagenDeProducto.
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductoDelCatalogoDto(String id, String nombre, String descripcion, String tipo,
                                     Integer precioCreditos, BigDecimal precioMonedaReal, Boolean premium,
                                     Promocion promocion, String imagen) {

    public ProductoDelCatalogoDto(String id, String nombre, String descripcion, String tipo,
                                  Integer precioCreditos, BigDecimal precioMonedaReal, Boolean premium,
                                  Promocion promocion) {
        this(id, nombre, descripcion, tipo, precioCreditos, precioMonedaReal, premium, promocion, null);
    }

    // La promocion solo llega mientras esta vigente (proyeccion publica, 1.4.0).
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Promocion(Integer porcentaje, String hasta) {
    }

    public boolean esPremium() {
        return Boolean.TRUE.equals(premium);
    }
}
