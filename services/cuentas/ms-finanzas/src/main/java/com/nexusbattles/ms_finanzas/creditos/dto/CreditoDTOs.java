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

    public record AcreditarRequest(
        String uid,
        BigDecimal monto,
        String refId,
        String concepto
    ) {}

    public record AcreditarResponse(
        String transaccionId,
        String refId,
        String estado,
        BigDecimal montoAcreditado,
        BigDecimal nuevoSaldoDisponible
    ) {}

    /**
     * Una linea del historial de creditos del jugador (#569).
     *
     * <p>`tipo` dice que clase de operacion fue (RESERVA, DEBITO, CREDITO),
     * `estado` en que quedo (ACTIVA, LIBERADA, CONSUMIDA) y `signo` resume
     * para la interfaz si el saldo subio, bajo o quedo apartado, para que la
     * vista no tenga que reimplementar esa regla.
     */
    public record MovimientoResponse(
        java.util.UUID id,
        java.math.BigDecimal monto,
        String concepto,
        String referenciaId,
        String tipo,
        String estado,
        String signo,
        java.time.OffsetDateTime creado) { }
}
