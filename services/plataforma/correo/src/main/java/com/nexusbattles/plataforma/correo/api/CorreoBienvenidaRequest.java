package com.nexusbattles.plataforma.correo.api;

import com.nexusbattles.plataforma.correo.cola.CorreoPedido;
import com.nexusbattles.plataforma.correo.template.Plantilla;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

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

    public CorreoPedido aCorreo() {
        return CorreoPedido.paraEnviar(
                Plantilla.BIENVENIDA, email, "Bienvenido a The Nexus Battles VI", Map.of("saludo", saludo()));
    }
}
