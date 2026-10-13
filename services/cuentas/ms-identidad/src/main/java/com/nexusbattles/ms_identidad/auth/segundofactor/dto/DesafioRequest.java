package com.nexusbattles.ms_identidad.auth.segundofactor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code DesafioRequest} de ms-identidad-auth.yaml 2.2.0 (enrolamiento obligatorio sin sesion). */
public record DesafioRequest(@NotBlank @Size(max = 128) String desafio) {

    @Override
    public String toString() {
        return "DesafioRequest[desafio=********]";
    }
}
