package com.nexusbattles.ms_ecommerce.compra;

/**
 * El asiento de la orden en el libro de moneda real de ms-finanzas
 * ({@code POST /transacciones}).
 */
public enum EstadoDelAsiento {
    /** Hay que registrarlo y aun no se pudo. */
    PENDIENTE,
    /** ms-finanzas lo tiene (201, o 409 si ya estaba). */
    REGISTRADO,
    /**
     * No hay nada que registrar: la orden nunca llego a un resultado de la
     * pasarela (caduco PENDIENTE), o se compenso y el libro no tiene reembolsos.
     */
    NO_APLICA
}
