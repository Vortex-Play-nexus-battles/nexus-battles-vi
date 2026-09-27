package com.nexusbattles.ms_ecommerce.compra.pago;

/** Lo que respondio la pasarela simulada a un cobro. */
public sealed interface ResultadoDeCobro {

    /** Cobro aprobado, con la referencia de la pasarela (va al asiento de ms-finanzas). */
    record Aprobado(String referencia) implements ResultadoDeCobro {
    }

    /** La pasarela se nego (fondos insuficientes): no se cobro nada. */
    record Rechazado(String motivo) implements ResultadoDeCobro {
    }

    /** La pasarela no respondio: no se cobro nada y se puede reintentar. */
    record NoDisponible() implements ResultadoDeCobro {
    }
}
