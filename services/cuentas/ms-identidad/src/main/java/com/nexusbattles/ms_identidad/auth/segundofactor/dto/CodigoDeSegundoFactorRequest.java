package com.nexusbattles.ms_identidad.auth.segundofactor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code CodigoDeSegundoFactorRequest} de ms-identidad-auth.yaml 2.2.0. Que sean
 * seis cifras lo decide el servicio (un codigo con otra forma es simplemente
 * incorrecto); aqui solo se exige que venga.
 */
public record CodigoDeSegundoFactorRequest(@NotBlank @Size(max = 32) String codigo) {

    @Override
    public String toString() {
        return "CodigoDeSegundoFactorRequest[codigo=******]";
    }
}
