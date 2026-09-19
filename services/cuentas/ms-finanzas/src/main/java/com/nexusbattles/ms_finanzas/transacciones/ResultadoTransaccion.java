package com.nexusbattles.ms_finanzas.transacciones;

/**
 * Estado final de una transacción en moneda real (HU-PAG-002).
 *
 * <ul>
 *   <li>{@link #APROBADO} — la pasarela confirmó el cargo.</li>
 *   <li>{@link #RECHAZADO} — la pasarela negó el cargo (fondos, tarjeta bloqueada, etc.).</li>
 *   <li>{@link #INDETERMINADO} — no llegó respuesta a tiempo o llegó ambigua; queda marcada
 *       para conciliación manual (criterio de aceptación de HU-PAG-002).</li>
 * </ul>
 */
public enum ResultadoTransaccion {
    APROBADO,
    RECHAZADO,
    INDETERMINADO
}
