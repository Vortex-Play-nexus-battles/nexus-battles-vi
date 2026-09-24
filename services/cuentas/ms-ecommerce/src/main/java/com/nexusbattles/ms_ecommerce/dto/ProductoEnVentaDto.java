package com.nexusbattles.ms_ecommerce.dto;

import java.math.BigDecimal;

/**
 * Un producto de la vitrina ({@code GET /api/v1/vitrina}), proyectado del
 * catalogo maestro.
 *
 * <p>Misma forma que {@link ProductoVitrinaDto} —el frontend ya la sabe leer—
 * salvo el {@code id}, que ahora es el UUID del catalogo (texto) y no el BIGINT
 * de la tabla local. Es otro tipo y no un cambio de {@code ProductoVitrinaDto}
 * para que el endpoint legado {@code GET /api/v1/productos} siga devolviendo
 * exactamente lo mismo que antes.
 *
 * <p>Lo que la tienda todavia no calcula se dice tal cual, sin inventar:
 * no hay promociones ({@code precioOriginal} = {@code precioFinal},
 * {@code enPromocion} false, {@code porcentajeDescuento} nulo), no hay
 * conversion de moneda (siempre COP) y ni la propiedad ni la lista de deseos
 * se cruzan todavia (false).
 */
public record ProductoEnVentaDto(
        String id,
        String nombre,
        String imagenUrl,
        String descripcion,
        String habilidades,
        String tipo,
        BigDecimal precioFinal,
        BigDecimal precioOriginal,
        String moneda,
        Boolean enPromocion,
        Integer porcentajeDescuento,
        Boolean esPropio,
        Boolean enListaDeseos) {
}
