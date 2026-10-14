package com.nexusbattles.ms_identidad.sanciones.apelacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * {@code POST /auth/apelacion}: el codigo del login, la sancion que la
 * persona vio en la consulta y su argumento (§7.3.2, como cualquier
 * apelacion: hasta 2000 caracteres).
 */
public record ApelacionSinSesionRequest(@NotBlank @Size(max = 128) String codigo,
                                        @NotNull UUID sancionId,
                                        @NotBlank @Size(max = 2000) String argumento) {

    @Override
    public String toString() {
        return "ApelacionSinSesionRequest[codigo=********, sancionId=" + sancionId + "]";
    }
}
