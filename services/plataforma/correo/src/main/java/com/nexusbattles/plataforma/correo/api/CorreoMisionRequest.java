package com.nexusbattles.plataforma.correo.api;

import com.nexusbattles.plataforma.correo.cola.CorreoPedido;
import com.nexusbattles.plataforma.correo.template.Plantilla;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

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
 * apago esa categoria (CA-02/CA-03). Se responde 202 igual y el correo queda
 * en la cola como OMITIDO, sin contenido: la evidencia de entrega puede decir
 * por que no salio, y nada se envia.
 */
public record CorreoMisionRequest(
        @NotBlank @Email String email,
        @NotBlank String apodo,
        @NotBlank String asunto,
        @NotBlank String mensaje,
        @NotNull Boolean debeEnviarCorreo) {

    /** Por que no sale un correo de misiones o de subastas con debeEnviarCorreo=false. */
    static final String MOTIVO_PREFERENCIA =
            "el jugador no quiere esta categoria por correo (debeEnviarCorreo=false)";

    public CorreoPedido aCorreo() {
        if (!debeEnviarCorreo) {
            return CorreoPedido.suprimido(Plantilla.MISION, email, asunto, MOTIVO_PREFERENCIA);
        }
        return CorreoPedido.paraEnviar(
                Plantilla.MISION, email, asunto, Map.of("apodo", apodo, "asunto", asunto, "mensaje", mensaje));
    }
}
