package com.nexusbattles.plataforma.correo.envio;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Datos del aviso de acceso desde un dispositivo o ubicación no reconocidos
 * (RF-AUT-010). Lo dispara ms-identidad desde LoginService.
 */
public record CorreoAvisoAccesoRequest(
        @NotBlank @Email String email,
        @NotBlank String apodo,
        @NotBlank String ip,
        @NotNull OffsetDateTime fechaHora) {

    private static final DateTimeFormatter LEGIBLE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm (OOOO)", new Locale("es"));

    /** El destinatario es una persona: no se le muestra un ISO-8601 crudo. */
    public String fechaHoraLegible() {
        return fechaHora.format(LEGIBLE);
    }
}
