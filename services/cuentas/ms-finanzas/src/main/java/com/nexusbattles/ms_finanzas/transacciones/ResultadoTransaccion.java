package com.nexusbattles.ms_finanzas.transacciones;

/**
 * Estado final de una transacciÃ³n en moneda real (HU-PAG-002).
 *
 * <ul>
 *   <li>{@link #APROBADO} â€” la pasarela confirmÃ³ el cargo.</li>
 *   <li>{@link #RECHAZADO} â€” la pasarela negÃ³ el cargo (fondos, tarjeta bloqueada, etc.).</li>
 *   <li>{@link #INDETERMINADO} â€” no llegÃ³ respuesta a tiempo o llegÃ³ ambigua; queda marcada
 *       para conciliaciÃ³n manual (criterio de aceptaciÃ³n de HU-PAG-002).</li>
 * </ul>
 */
public enum ResultadoTransaccion {
    APROBADO,
    RECHAZADO,
    INDETERMINADO,

    /**
     * La operacion se aplico y despues se compenso: el saldo volvio al
     * jugador. Es un estado final distinto de RECHAZADO, donde el movimiento
     * nunca llego a aplicarse. Lo usa CreditoService.reversar para que un
     * segundo reverso del mismo refId no devuelva el dinero dos veces.
     *
     * <p>No necesita migracion: la columna es VARCHAR(32) con la enumeracion
     * guardada por nombre.
     */
    REVERSADO
}
