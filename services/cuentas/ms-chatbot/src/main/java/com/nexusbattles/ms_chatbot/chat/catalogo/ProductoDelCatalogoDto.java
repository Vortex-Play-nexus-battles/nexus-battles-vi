package com.nexusbattles.ms_chatbot.chat.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

// productos.yaml: un elemento de GET /api/v1/productos en la proyeccion
// publica (sin token). Solo lo que el chat usa: nada de campos internos.
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductoDelCatalogoDto(String id, String nombre, String descripcion, String tipo,
                                     Integer precioCreditos, BigDecimal precioMonedaReal, Boolean premium,
                                     Promocion promocion) {

    // La promocion solo llega mientras esta vigente (proyeccion publica, 1.4.0).
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Promocion(Integer porcentaje, String hasta) {
    }

    public boolean esPremium() {
        return Boolean.TRUE.equals(premium);
    }
}
