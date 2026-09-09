package com.nexusbattles.plataforma.correo.envio;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Datos del correo de bienvenida. Definido en
 * contracts/openapi/correo.yaml — lo consume ms-identidad desde RegistroService.
 *
 * <p>No incluye ningún campo de contenido ni de HTML a propósito: la apariencia
 * la pone la plantilla corporativa (HU-COR-001), nunca el llamante.
 */
public record CorreoBienvenidaRequest(
        @NotBlank @Email String email,
        @NotBlank String apodo,
        String nombres,
        String apellidos) {

    /** Prefiere el nombre real si vino; si no, el apodo. */
    public String saludo() {
        return (nombres == null || nombres.isBlank()) ? apodo : nombres.trim();
    }
}
