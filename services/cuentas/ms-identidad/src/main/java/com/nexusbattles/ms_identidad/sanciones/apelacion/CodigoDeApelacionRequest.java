package com.nexusbattles.ms_identidad.sanciones.apelacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /auth/apelacion/consulta}: el codigo que entrego el login. */
public record CodigoDeApelacionRequest(@NotBlank @Size(max = 128) String codigo) {

    @Override
    public String toString() {
        return "CodigoDeApelacionRequest[codigo=********]";
    }
}
