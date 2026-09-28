package com.nexusbattles.plataforma.correo.api;

import com.nexusbattles.plataforma.correo.cola.CorreoPedido;
import com.nexusbattles.plataforma.correo.template.Plantilla;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Datos del aviso de acceso desde un dispositivo o ubicación no reconocidos
 * (RF-AUT-010). Lo dispara ms-identidad desde LoginService.
 */
public record CorreoAvisoAccesoRequest(
        @NotBlank @Email String email,
        @NotBlank String apodo,
        @NotBlank String ip,
        @NotNull OffsetDateTime fechaHora) {

    /** El destinatario es una persona: no se le muestra un ISO-8601 crudo. */
    public String fechaHoraLegible() {
        return FechaLegible.de(fechaHora);
    }

    public CorreoPedido aCorreo() {
        return CorreoPedido.paraEnviar(
                Plantilla.AVISO_ACCESO,
                email,
                "Acceso desde un dispositivo no reconocido",
                Map.of("apodo", apodo, "ip", ip, "fechaHora", fechaHoraLegible()));
    }
}
