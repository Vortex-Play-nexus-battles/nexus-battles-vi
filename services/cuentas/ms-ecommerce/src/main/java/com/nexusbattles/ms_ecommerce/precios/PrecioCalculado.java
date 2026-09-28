package com.nexusbattles.ms_ecommerce.precios;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * El precio de una unidad tal como se ensena y se cobra, en una moneda.
 *
 * @param moneda               la moneda de los dos importes
 * @param precioOriginal       antes de la promocion
 * @param precioFinal          el que se cobra: con la promocion vigente aplicada
 * @param porcentajeDescuento  el de la promocion vigente; null sin promocion
 */
public record PrecioCalculado(Moneda moneda, BigDecimal precioOriginal, BigDecimal precioFinal,
                              Integer porcentajeDescuento) {

    public PrecioCalculado {
        Objects.requireNonNull(moneda, "moneda");
        Objects.requireNonNull(precioOriginal, "precioOriginal");
        Objects.requireNonNull(precioFinal, "precioFinal");
    }

    /** Hay una promocion vigente que rebaja el precio. */
    public boolean enPromocion() {
        return porcentajeDescuento != null;
    }

    /** Precio final por cantidad: el subtotal de una linea, sin volver a redondear. */
    public BigDecimal subtotal(int cantidad) {
        return precioFinal.multiply(BigDecimal.valueOf(cantidad));
    }
}
