package com.nexusbattles.ms_ecommerce.compra;

import java.util.List;

/**
 * Lo que costaria pagar el carrito con creditos del juego ({@code GET
 * /checkout/creditos}, contrato 1.6.0, D-44), calculado en el servidor: la
 * interfaz lo ensena tal cual —saldo actual, precio, saldo despues— y no suma
 * ni resta nada.
 *
 * @param pagable         el carrito se puede pagar con creditos ahora (no dice
 *                        si el saldo alcanza: eso es {@code alcanza})
 * @param motivo          por que no es pagable; null si lo es
 * @param totalCreditos   la suma de los subtotales; null si no es pagable
 * @param saldoDisponible el de ms-finanzas en creditos enteros; null si no respondio
 * @param saldoDespues    saldoDisponible - totalCreditos; null si falta alguno
 * @param alcanza         saldoDespues >= 0; null si no se sabe
 */
public record CotizacionEnCreditos(
        boolean pagable,
        Motivo motivo,
        List<Linea> lineas,
        Long totalCreditos,
        Long saldoDisponible,
        Long saldoDespues,
        Boolean alcanza) {

    public CotizacionEnCreditos {
        lineas = List.copyOf(lineas);
    }

    /** Por que no se puede pagar con creditos. */
    public enum Motivo {
        /** No hay nada del catalogo en el carrito. */
        CARRITO_VACIO,
        /** Algun producto ya no se puede comprar: agotado, suspendido, fuera del catalogo o sin tantas unidades. */
        PRODUCTO_NO_DISPONIBLE,
        /** Algun producto no tiene precio en creditos (premium, o sin precioCreditos en el catalogo). */
        SIN_PRECIO_EN_CREDITOS
    }

    /**
     * Una linea del carrito con su precio en creditos.
     *
     * @param disponible       se puede comprar ahora (mismo criterio que el carrito)
     * @param precioCreditos   unitario, promocion incluida; null sin precio en creditos
     * @param subtotalCreditos precioCreditos x cantidad; null sin precio en creditos
     */
    public record Linea(String productoId, String nombre, int cantidad, boolean disponible, Long precioCreditos,
                        Long subtotalCreditos, Integer porcentajeDescuento) {
    }
}
