package com.nexusbattles.ms_identidad.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /admin/usuarios/{id}/segundo-factor/restablecimiento}: por que se restablece (queda en la auditoria). */
public record RestablecerSegundoFactorRequest(@NotBlank @Size(min = 5, max = 500) String motivo) {
}
