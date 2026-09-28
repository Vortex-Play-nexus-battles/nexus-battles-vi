package com.nexusbattles.ms_finanzas.pagos.correo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ConfirmacionCompraRequest(
    String email,
    String apodo,
    BigDecimal monto,
    String moneda,
    String concepto,
    OffsetDateTime fechaHora
) {}
