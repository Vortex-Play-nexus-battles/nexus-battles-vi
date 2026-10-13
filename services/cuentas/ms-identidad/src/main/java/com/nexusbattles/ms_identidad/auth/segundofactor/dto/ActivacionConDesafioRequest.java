package com.nexusbattles.ms_identidad.auth.segundofactor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code ActivacionConDesafioRequest} de ms-identidad-auth.yaml 2.2.0 (enrolamiento obligatorio sin sesion). */
public record ActivacionConDesafioRequest(@NotBlank @Size(max = 128) String desafio,
                                          @NotBlank @Size(max = 32) String codigo) {

    @Override
    public String toString() {
        return "ActivacionConDesafioRequest[desafio=********, codigo=******]";
    }
}
