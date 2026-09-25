package com.nexusbattles.plataforma.correo.api;

import com.nexusbattles.plataforma.correo.cola.CorreoPedido;
import com.nexusbattles.plataforma.correo.template.Plantilla;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Datos del aviso de cambio de contraseña (HU-AUT-006, RF-AUT-006). Lo
 * dispara ms-identidad desde CambioDePasswordService. Nunca lleva la
 * contraseña: solo que cambió, desde dónde y cuándo.
 */
public record CorreoCambioClaveRequest(
        @NotBlank @Email String email,
        @NotBlank String apodo,
        @NotBlank String ip,
        @NotNull OffsetDateTime fechaHora) {

    /** El destinatario es una persona: no se le muestra un ISO-8601 crudo. */
    public String fechaHoraLegible() {
        return FechaLegible.de(fechaHora);
    }

    public CorreoPedido aCorreo() {
        // HU-AUT-006 CA-01. Mismo caracter que el aviso de acceso: informa
        // de algo que YA paso para que quien no lo hizo reaccione.
        return CorreoPedido.paraEnviar(
                Plantilla.CAMBIO_CLAVE,
                email,
                "Tu contraseña de The Nexus Battles VI cambió",
                Map.of("apodo", apodo, "ip", ip, "fechaHora", fechaHoraLegible()));
    }
}
