package com.nexusbattles.plataforma.correo.envio;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos del correo de confirmación de cuenta (HU-COR-002, RNF-COR-002).
 *
 * <p>Mismo reparto de responsabilidades que en HU-COR-003: este servicio solo
 * renderiza y envía el correo. Generar el código de forma segura, guardarlo
 * con su marca de tiempo, validarlo, invalidar el anterior al reenviar y
 * bloquear tras varios intentos fallidos corresponde a ms-identidad, que es
 * quien registra la cuenta y la deja «pendiente de verificación». Identidad
 * y control de acceso están fuera del alcance del Equipo 6 (project-charter.md).
 */
public record CorreoConfirmacionCuentaRequest(
        @NotBlank @Email String email,
        @NotBlank String apodo,

        /*
         * No se valida que sean seis dígitos: eso acoplaría este servicio al
         * formato que hoy usa ms-identidad. Si mañana lo cambian, el correo
         * seguiría funcionando.
         */
        @NotBlank @Size(max = 12) String codigo,

        /*
         * La vigencia la manda quien generó el código: el issue pide que sea
         * configurable por variable de entorno del lado de quien lo emite, y
         * así el correo nunca promete un plazo distinto del real.
         */
        @NotNull @Min(1) Integer minutosVigencia) {
}
