package com.nexusbattles.ms_ecommerce.compra;

/**
 * Como se pago una orden (contrato 1.6.0, {@code Orden.formaDePago}).
 *
 * <p>Los dos caminos comparten la orden, sus estados, sus claves y lo que pasa
 * despues de cobrar (reservar el tiraje, entregar, compensar). Cambia el cobro
 * y lo que se registra: con tarjeta, la pasarela simulada y el asiento en el
 * libro de moneda real; con creditos, el debito en el libro de creditos de
 * ms-finanzas y nada mas.
 */
public enum FormaDePago {

    /** La pasarela simulada ({@code POST /checkout}, 7.5). */
    TARJETA,

    /**
     * El saldo de creditos del juego en ms-finanzas
     * ({@code POST /checkout/creditos}, D-44): metodo adicional, con el
     * {@code precioCreditos} de cada producto (7.2.1).
     */
    CREDITOS
}
