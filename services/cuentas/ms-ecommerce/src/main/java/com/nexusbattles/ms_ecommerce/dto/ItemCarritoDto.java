package com.nexusbattles.ms_ecommerce.dto;

import java.math.BigDecimal;

/**
 * Una linea del carrito:
 * {@code {"id":10,"producto":{...},"cantidad":1,"precioUnitario":6000,"subtotal":6000,"disponible":true,...}}.
 *
 * @param precioUnitario      (1.4.0) lo que se cobraria ahora por unidad, promocion
 *                            incluida, en la moneda del carrito; null si el producto
 *                            solo se vende en creditos (1.7.0)
 * @param precioOriginal      (1.4.0) antes de la promocion
 * @param porcentajeDescuento (1.4.0) el de la promocion vigente; null sin promocion
 * @param disponible          (1.4.0) si la linea se puede comprar ahora (con alguna
 *                            forma de pago)
 * @param motivo              (1.4.0) por que no; null si se puede
 * @param maximo              (1.4.0) hasta cuantas unidades admite: 20, o las que
 *                            le quedan al producto si son menos; null si no se sabe
 * @param precioCreditos      (1.7.0, G3) lo que cuesta una unidad pagada con creditos
 *                            del juego, promocion incluida; null si no se puede pagar
 *                            asi o si el catalogo no respondio
 * @param subtotalCreditos    (1.7.0, G3) precioCreditos por cantidad, calculado aqui
 *                            para que el navegador no multiplique precios; null cuando
 *                            precioCreditos es null
 * @param soloEnCreditos      (1.7.0, G3) el producto no tiene precio en dinero real:
 *                            esta linea solo se paga con creditos y no suma al total
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
        Integer maximo,
        Long precioCreditos,
        Long subtotalCreditos,
        boolean soloEnCreditos) {

    /** Sin precio en creditos: la forma de 1.4.0 a 1.6.0. */
    public ItemCarritoDto(Long id, ProductoDelItemDto producto, Integer cantidad, BigDecimal precioUnitario,
                          BigDecimal precioOriginal, Integer porcentajeDescuento, BigDecimal subtotal,
                          boolean disponible, MotivoDeLinea motivo, Integer maximo) {
        this(id, producto, cantidad, precioUnitario, precioOriginal, porcentajeDescuento, subtotal, disponible, motivo,
                maximo, null, null, false);
    }

    /**
     * (1.7.0) Con el precio en creditos de una unidad: el subtotal en creditos
     * es ese precio por la cantidad.
     */
    public static Long subtotalEnCreditos(Long precioCreditos, Integer cantidad) {
        return precioCreditos == null || cantidad == null ? null : Math.multiplyExact(precioCreditos, (long) cantidad);
    }

    /** Una linea que se puede comprar, sin promocion ni tope conocido: la forma anterior a 1.4.0. */
    public ItemCarritoDto(Long id, ProductoDelItemDto producto, Integer cantidad, BigDecimal precioUnitario,
                          BigDecimal subtotal) {
        this(id, producto, cantidad, precioUnitario, precioUnitario, null, subtotal, true, null, null);
    }
}
