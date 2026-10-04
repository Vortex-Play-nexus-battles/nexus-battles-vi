package com.nexusbattles.ms_ecommerce.precios;

/**
 * El precio de una unidad pagada con creditos del juego (D-44), en creditos
 * enteros: el {@code precioCreditos} del catalogo y, si hay una promocion
 * vigente, el mismo precio rebajado.
 *
 * @param precioOriginal      el {@code precioCreditos} del catalogo
 * @param precioFinal         el que se cobra: con la promocion vigente aplicada
 * @param porcentajeDescuento el de la promocion vigente; null sin promocion
 */
public record PrecioEnCreditos(long precioOriginal, long precioFinal, Integer porcentajeDescuento) {

    public PrecioEnCreditos {
        if (precioOriginal < 1 || precioFinal < 1) {
            throw new IllegalArgumentException("Un precio en creditos es de al menos 1 credito");
        }
    }

    /** Precio final por cantidad: el subtotal de una linea. */
    public long subtotal(int cantidad) {
        return Math.multiplyExact(precioFinal, (long) cantidad);
    }
}
