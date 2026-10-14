package com.nexusbattles.plataforma.correo.api;

import com.nexusbattles.plataforma.correo.cola.CorreoPedido;
import com.nexusbattles.plataforma.correo.template.Plantilla;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Confirmacion de que la cuenta se elimino — correo.yaml 1.7.0 (SA-14,
 * §7.3.6 «notificacion de confirmacion al completar la eliminacion»). Lo
 * dispara ms-identidad al terminar de anonimizar la cuenta, con la direccion
 * y el apodo que tenia.
 */
public record CorreoCierreCuentaRequest(
        @NotBlank @Email String email,
        @NotBlank String apodo,
        @NotNull OffsetDateTime fechaHora) {

    static final String ASUNTO = "Tu cuenta de The Nexus Battles VI fue eliminada";

    /** El destinatario es una persona: no se le muestra un ISO-8601 crudo. */
    public String fechaHoraLegible() {
        return FechaLegible.de(fechaHora);
    }

    public CorreoPedido aCorreo() {
        return CorreoPedido.paraEnviar(Plantilla.CIERRE_CUENTA, email, ASUNTO,
                Map.of("apodo", apodo, "fechaHora", fechaHoraLegible()));
    }
}
