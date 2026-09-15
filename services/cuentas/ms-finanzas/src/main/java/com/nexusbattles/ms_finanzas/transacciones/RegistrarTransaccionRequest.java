package com.nexusbattles.ms_finanzas.transacciones;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Entrada del writer {@link TransaccionRegistroService#registrar}. Es un DTO
 * interno del dominio de finanzas; los servicios que consumen ms-finanzas por
 * REST no ven esta clase. La usará HU-PAG-001 (Juan Diego) al persistir cada
 * intento contra la pasarela simulada.
 *
 * <p>Los campos {@code comprobanteUrl} y {@code pasarelaRefExterna} son
 * opcionales: llegan cuando la pasarela responde, se dejan {@code null} en el
 * primer registro y se actualizan cuando la conciliación resuelve el estado.
 */
public record RegistrarTransaccionRequest(
        String refId,
        String uidUsuario,
        BigDecimal monto,
        String moneda,
        String concepto,
        ResultadoTransaccion resultado,
        String comprobanteUrl,
        String pasarelaRefExterna) {

    public RegistrarTransaccionRequest {
        Objects.requireNonNull(refId, "refId es requerido");
        if (refId.isBlank()) {
            throw new IllegalArgumentException("refId no puede estar vacío");
        }
        Objects.requireNonNull(uidUsuario, "uidUsuario es requerido");
        if (uidUsuario.isBlank()) {
            throw new IllegalArgumentException("uidUsuario no puede estar vacío");
        }
        Objects.requireNonNull(monto, "monto es requerido");
        if (monto.signum() < 0) {
            throw new IllegalArgumentException("monto no puede ser negativo");
        }
        Objects.requireNonNull(moneda, "moneda es requerida");
        if (moneda.length() != 3) {
            throw new IllegalArgumentException("moneda debe ser código ISO 4217 de 3 letras");
        }
        Objects.requireNonNull(concepto, "concepto es requerido");
        if (concepto.isBlank()) {
            throw new IllegalArgumentException("concepto no puede estar vacío");
        }
        Objects.requireNonNull(resultado, "resultado es requerido");
    }
}
