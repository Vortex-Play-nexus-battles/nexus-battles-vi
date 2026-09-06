package com.nexusbattles.plataforma.correo.envio;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos del correo de recuperación de contraseña (HU-COR-003).
 *
 * <p>Este servicio solo envía el correo. Generar, guardar, validar e invalidar
 * el código de un solo uso corresponde a ms-identidad: identidad y control de
 * acceso están fuera del alcance del Equipo 6 (project-charter.md).
 */
public record CorreoRecuperacionClaveRequest(
        @NotBlank @Email String email,
        @NotBlank String apodo,

        /*
         * No se valida que sean seis dígitos: eso acoplaría este servicio al
         * formato que hoy usa ms-identidad. Si mañana lo cambian, el correo
         * seguiría funcionando.
         */
        @NotBlank @Size(max = 12) String codigo,

        @NotNull @Min(1) Integer minutosVigencia) {
}
