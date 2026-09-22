package com.nexusbattles.plataforma.correo.envio;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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

    private static final DateTimeFormatter LEGIBLE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm (OOOO)", new Locale("es"));

    /** El destinatario es una persona: no se le muestra un ISO-8601 crudo. */
    public String fechaHoraLegible() {
        return fechaHora.format(LEGIBLE);
    }
}
