package com.nexusbattles.ms_ecommerce.dto;

import java.math.BigDecimal;

/**
 * Una linea del carrito:
 * {@code {"id":10,"producto":{...},"cantidad":1,"precioUnitario":6000.00,"subtotal":6000.00}}.
 *
 * @param precioUnitario el precio en moneda real que tenia el producto en el
 *                       catalogo cuando se agrego (o se volvio a agregar)
 */
public record ItemCarritoDto(
        Long id,
        ProductoDelItemDto producto,
        Integer cantidad,
        BigDecimal precioUnitario,
        BigDecimal subtotal) {
}
