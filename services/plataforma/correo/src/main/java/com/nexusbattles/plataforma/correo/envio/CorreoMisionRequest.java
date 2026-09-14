package com.nexusbattles.plataforma.correo.envio;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Correo de un evento de misiones (HU-COR-005).
 *
 * <p>Este servicio no decide que se notifica ni si corresponde enviar
 * correo: eso ya lo resolvio el modulo de misiones, incluida la preferencia
 * del jugador. "asunto" y "mensaje" los trae quien llama, porque solo
 * misiones conoce el evento concreto (mision nueva, completada, etc.).
 *
 * <p>"debeEnviarCorreo" en false no es un error: es la forma en que misiones
 * indica que el jugador pidio avisos solo dentro de la aplicacion, o que
 * apago esa categoria (CA-02/CA-03). El registro de esa supresion es
 * responsabilidad de quien llama, no de este servicio.
 */
public record CorreoMisionRequest(
        @NotBlank @Email String email,
        @NotBlank String apodo,
        @NotBlank String asunto,
        @NotBlank String mensaje,
        @NotNull Boolean debeEnviarCorreo) {
}
