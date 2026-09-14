package com.nexusbattles.plataforma.correo.envio;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Correo de un evento de subastas (HU-COR-005).
 *
 * <p>Mismo criterio que CorreoMisionRequest: este servicio no decide el
 * contenido ni si corresponde enviar, solo lo transcribe sobre la plantilla
 * corporativa. Plantilla separada de la de misiones para poder evolucionar
 * cada una por su lado (por ejemplo, resaltar la puja ganadora aqui) sin
 * tocar la otra.
 */
public record CorreoSubastaRequest(
        @NotBlank @Email String email,
        @NotBlank String apodo,
        @NotBlank String asunto,
        @NotBlank String mensaje,
        @NotNull Boolean debeEnviarCorreo) {
}
