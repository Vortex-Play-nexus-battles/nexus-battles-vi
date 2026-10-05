package com.nexusbattles.ms_identidad.privacidad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code SolicitarCierre} de ms-identidad-perfiles.yaml 1.4.0: la contrasena
 * actual como verificacion de identidad.
 *
 * <p>{@link #toString()} la tapa: ninguna bitacora la escribe.
 */
public record SolicitarCierreRequest(@NotBlank @Size(max = 255) String passwordActual) {

    @Override
    public String toString() {
        return "SolicitarCierreRequest[passwordActual=********]";
    }
}
