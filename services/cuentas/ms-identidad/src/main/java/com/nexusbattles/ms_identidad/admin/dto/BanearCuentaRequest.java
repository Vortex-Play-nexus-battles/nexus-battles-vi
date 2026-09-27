package com.nexusbattles.ms_identidad.admin.dto;

import jakarta.validation.constraints.Size;

/**
 * Cuerpo OPCIONAL de {@code PUT /admin/usuarios/{id}/banear} (B2): la causal
 * documentada del baneo (7.3.2 «Causales de sancion»), que queda en el
 * historial de moderacion-sanciones. Sin cuerpo se usa un motivo generico.
 */
public record BanearCuentaRequest(@Size(max = 1000) String motivo) {
}
