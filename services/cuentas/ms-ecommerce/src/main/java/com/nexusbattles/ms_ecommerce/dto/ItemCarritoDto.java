package com.nexusbattles.ms_ecommerce.dto;

import java.math.BigDecimal;

/**
 * Una linea del carrito:
 * {@code {"id":10,"producto":{...},"cantidad":1,"precioUnitario":6000,"subtotal":6000,"disponible":true,...}}.
 *
 * @param precioUnitario      (1.4.0) lo que se cobraria ahora por unidad, promocion
 *                            incluida, en la moneda del carrito
 * @param precioOriginal      (1.4.0) antes de la promocion
 * @param porcentajeDescuento (1.4.0) el de la promocion vigente; null sin promocion
 * @param disponible          (1.4.0) si la linea se puede comprar ahora
 * @param motivo              (1.4.0) por que no; null si se puede
 * @param maximo              (1.4.0) hasta cuantas unidades admite: 20, o las que
 *                            le quedan al producto si son menos; null si no se sabe
 */
public record ItemCarritoDto(
        Long id,
        ProductoDelItemDto producto,
        Integer cantidad,
        BigDecimal precioUnitario,
        BigDecimal precioOriginal,
        Integer porcentajeDescuento,
        BigDecimal subtotal,
        boolean disponible,
        MotivoDeLinea motivo,
        Integer maximo) {

    /** Una linea que se puede comprar, sin promocion ni tope conocido: la forma anterior a 1.4.0. */
    public ItemCarritoDto(Long id, ProductoDelItemDto producto, Integer cantidad, BigDecimal precioUnitario,
                          BigDecimal subtotal) {
        this(id, producto, cantidad, precioUnitario, precioUnitario, null, subtotal, true, null, null);
    }
}
