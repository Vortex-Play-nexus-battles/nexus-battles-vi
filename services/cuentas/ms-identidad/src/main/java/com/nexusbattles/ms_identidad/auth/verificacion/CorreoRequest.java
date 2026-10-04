package com.nexusbattles.ms_identidad.auth.verificacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo del reenvio ({@code SolicitarRestablecimientoRequest} en el contrato: solo el correo). */
public record CorreoRequest(@NotBlank @Size(max = 254) String email) {
}
