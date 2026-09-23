package com.nexusbattles.ms_finanzas.transacciones;

import java.math.BigDecimal;

/**
 * Cuerpo de {@code POST /transacciones} (HU-PAG-002). Es el DTO público que
 * ve el servicio que llama por REST — distinto de {@link RegistrarTransaccionRequest},
 * que es interno del dominio y no se expone tal cual en el borde HTTP.
 */
public record RegistrarTransaccionApiRequest(
        String refId,
        String uidUsuario,
        BigDecimal monto,
        String moneda,
        String concepto,
        ResultadoTransaccion resultado,
        String comprobanteUrl,
        String pasarelaRefExterna) {
}
