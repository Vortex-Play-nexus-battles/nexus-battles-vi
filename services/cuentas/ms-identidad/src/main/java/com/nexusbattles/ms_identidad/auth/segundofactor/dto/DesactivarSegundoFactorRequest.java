package com.nexusbattles.ms_identidad.auth.segundofactor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code DesactivarSegundoFactorRequest} de ms-identidad-auth.yaml 2.2.0: la
 * contrasena actual y, ademas, un codigo de la aplicacion o uno de
 * recuperacion (exactamente uno; eso lo comprueba el servicio).
 */
public record DesactivarSegundoFactorRequest(@NotBlank String passwordActual,
                                             @Size(max = 32) String codigo,
                                             @Size(max = 64) String codigoRecuperacion) {

    @Override
    public String toString() {
        return "DesactivarSegundoFactorRequest[passwordActual=********, codigo=" + (codigo == null ? "no" : "si")
                + ", codigoRecuperacion=" + (codigoRecuperacion == null ? "no" : "si") + "]";
    }
}
