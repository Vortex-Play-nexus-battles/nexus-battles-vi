package com.nexusbattles.plataforma.notificaciones.catalogo;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Un cambio del catalogo tal como lo entrega productos — esquema
 * {@code AlertaCambioCatalogo} de productos.yaml (1.6.0).
 *
 * <p>Es una copia propia del dato, no la clase de productos: cada servicio
 * tiene sus tipos y se habla solo por el contrato (regla 7). El {@code tipo}
 * va como texto para que un tipo nuevo de productos no rompa la lectura.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CambioDelCatalogo(
        String id,
        String productoId,
        String productoNombre,
        String tipo,
        String descripcion,
        Instant implementadaEn) {
}
