package com.nexusbattles.ms_identidad.auth.verificacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code CodigoDeCorreoRequest} de ms-identidad-auth.yaml: el correo de la
 * cuenta y el codigo que llego a el.
 *
 * <p>Sin {@code @Email} ni tamanos estrictos en el codigo a proposito: un
 * correo mal escrito o un codigo con otra longitud son, para quien pregunta,
 * lo mismo que un codigo incorrecto (400 {@code codigo-invalido}); responder
 * otra cosa enseñaria que forma tiene una respuesta «buena». Solo se cortan
 * las entradas absurdas.
 */
public record CodigoDeCorreoRequest(
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 64) String codigo) {

    /** Sin el codigo: un {@code toString} descuidado no lo deja en la bitacora. */
    @Override
    public String toString() {
        return "CodigoDeCorreoRequest[codigo=********]";
    }
}
