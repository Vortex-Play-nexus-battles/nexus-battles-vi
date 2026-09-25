package com.nexusbattles.ms_finanzas.pagos.dto;

import java.math.BigDecimal;

public class PagoDTOs {

    public record ProcesarPagoRequest(
        String uidUsuario,
        BigDecimal monto,
        String moneda,
        String concepto,
        String refId
    ) {}

    public record ProcesarPagoResponse(
        String refId,
        String estado,
        String mensaje,
        boolean marcadoParaRevisionManual
    ) {}
}
