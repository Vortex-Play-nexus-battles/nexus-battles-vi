package com.nexusbattles.ms_identidad.auth.segundofactor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code CanjeDeDesafioRequest} de ms-identidad-auth.yaml 2.2.0: el segundo
 * paso del login. {@code codigo} o {@code codigoRecuperacion}, exactamente uno
 * (lo comprueba el servicio: 400 {@code datos-invalidos}).
 */
public record CanjeDeDesafioRequest(@NotBlank @Size(max = 128) String desafio,
                                    @Size(max = 32) String codigo,
                                    @Size(max = 64) String codigoRecuperacion) {

    @Override
    public String toString() {
        return "CanjeDeDesafioRequest[desafio=********, codigo=" + (codigo == null ? "no" : "si")
                + ", codigoRecuperacion=" + (codigoRecuperacion == null ? "no" : "si") + "]";
    }
}
