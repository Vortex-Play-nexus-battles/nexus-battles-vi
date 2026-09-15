package com.nexusbattles.ms_finanzas.creditos.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class CreditoDTOs {

    public record SaldoResponse(
        String jugadorUid,
        BigDecimal saldoBruto,
        BigDecimal saldoReservado,
        BigDecimal saldoDisponible
    ) {}

    public record ReservarRequest(
        String jugadorUid,
        BigDecimal monto,
        String concepto,
        String referenciaId
    ) {}

    public record ReservaResponse(
        UUID reservaId,
        String jugadorUid,
        BigDecimal monto,
        String estado,
        OffsetDateTime expiraEn
    ) {}

    public record ConsumirRequest(
        String vendedorUid
    ) {}

    public record ConsumirResponse(
        UUID reservaId,
        String estado,
        BigDecimal montoDebitado,
        String vendedorUid,
        String transaccionId
    ) {}

    // DTOs adaptados para la integración directa con Edwin (ms-subastas)
    public record DebitarRequest(
        String uid,
        BigDecimal monto,
        String refId,
        String concepto
    ) {}

    public record DebitarResponse(
        String transaccionId,
        String refId,
        String estado,
        BigDecimal montoDebitado,
        BigDecimal nuevoSaldoDisponible
    ) {}

    public record ReversarRequest(
        String refId,
        String motivo
    ) {}

    public record ReversarResponse(
        String refId,
        String estado,
        BigDecimal montoReversado,
        String motivo
    ) {}

    public record OperacionResponse(
        String refId,
        String uid,
        BigDecimal monto,
        String concepto,
        String estado,
        OffsetDateTime fecha
    ) {}
}
