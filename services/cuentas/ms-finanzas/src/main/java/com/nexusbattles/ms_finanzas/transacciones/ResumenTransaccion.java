package com.nexusbattles.ms_finanzas.transacciones;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Proyección de lectura pensada para el historial que verá el jugador (o el
 * administrador cuando consulte a otro usuario). NO incluye la referencia
 * externa a la pasarela — ese dato solo interesa para conciliación interna,
 * exponerlo agrega ruido y filtraría un detalle del proveedor de pagos.
 */
public record ResumenTransaccion(
        UUID id,
        String refId,
        BigDecimal monto,
        String moneda,
        String concepto,
        ResultadoTransaccion resultado,
        String comprobanteUrl,
        Instant creado) {

    public static ResumenTransaccion desde(Transaccion transaccion) {
        return new ResumenTransaccion(
                transaccion.getId(),
                transaccion.getRefId(),
                transaccion.getMonto(),
                transaccion.getMoneda(),
                transaccion.getConcepto(),
                transaccion.getResultado(),
                transaccion.getComprobanteUrl(),
                transaccion.getCreado());
    }
}
