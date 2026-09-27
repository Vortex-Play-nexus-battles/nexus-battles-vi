package com.nexusbattles.ms_ecommerce.dto;

/**
 * Por que una linea del carrito no se puede comprar ahora (contrato 1.4.0,
 * {@code LineaDeCarrito.motivo}). La linea sigue en el carrito —es del
 * jugador— pero no suma al total y la compra la rechaza.
 */
public enum MotivoDeLinea {
    /** El catalogo ya no lo ofrece: suspendido, retirado, o una linea legada sin producto del catalogo. */
    NO_DISPONIBLE,
    /** No le quedan unidades. */
    AGOTADO,
    /** Ya no tiene precio en dinero real. */
    SIN_PRECIO_EN_MONEDA_REAL,
    /** Le quedan menos unidades que las de la linea: hay que bajar la cantidad. */
    TIRAJE_INSUFICIENTE
}
