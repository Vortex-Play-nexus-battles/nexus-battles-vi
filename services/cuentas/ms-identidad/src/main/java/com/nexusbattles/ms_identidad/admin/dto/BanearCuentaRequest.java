package com.nexusbattles.ms_identidad.admin.dto;

import com.nexusbattles.ms_identidad.sanciones.CausalesDeSancion;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo OPCIONAL de {@code PUT /admin/usuarios/{id}/banear} (B2): el motivo
 * del baneo, que queda en el historial de moderacion-sanciones. Sin cuerpo se
 * usa un motivo generico.
 *
 * @param motivo   el hecho concreto
 * @param causal   SA-4: una de las ocho causales de §7.3.2 ({@link CausalesDeSancion}); opcional
 * @param politica SA-4: la politica violada (§7.3.7); opcional
 */
public record BanearCuentaRequest(
        @Size(max = 1000) String motivo,
        @Pattern(regexp = CausalesDeSancion.PATRON, message = CausalesDeSancion.MENSAJE) String causal,
        @Size(max = 200) String politica) {

    /** Solo con el motivo, como antes de SA-4. */
    public BanearCuentaRequest(String motivo) {
        this(motivo, null, null);
    }
}
