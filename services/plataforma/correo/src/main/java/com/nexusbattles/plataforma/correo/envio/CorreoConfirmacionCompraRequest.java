package com.nexusbattles.plataforma.correo.envio;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Correo de confirmación de un pago aprobado (HU-PAG-003, issue #537). Lo
 * dispara ms-finanzas (Grupo 4/Cuentas) tras aprobar el pago en su pasarela
 * simulada; este servicio no valida ni conoce la transacción en sí, solo la
 * transcribe al correo. monto/moneda/concepto siguen la misma forma que
 * {@code Transaccion} en ms-finanzas (moneda: ISO 4217 en mayúsculas).
 */
public record CorreoConfirmacionCompraRequest(
        @NotBlank @Email String email,
        @NotBlank String apodo,
        @NotNull @Positive BigDecimal monto,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "debe ser un código ISO 4217 en mayúsculas, p. ej. COP") String moneda,
        @NotBlank String concepto,
        @NotNull OffsetDateTime fechaHora) {

    private static final DateTimeFormatter LEGIBLE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm (OOOO)", new Locale("es"));

    /** El destinatario es una persona: no se le muestra un ISO-8601 crudo. */
    public String fechaHoraLegible() {
        return fechaHora.format(LEGIBLE);
    }

    /** p. ej. "50000.00 COP" — sin símbolos de moneda que no cubran todas las divisas soportadas. */
    public String montoFormateado() {
        return monto.toPlainString() + " " + moneda;
    }
}
