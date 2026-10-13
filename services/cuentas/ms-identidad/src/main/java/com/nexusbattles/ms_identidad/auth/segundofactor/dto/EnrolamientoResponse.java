package com.nexusbattles.ms_identidad.auth.segundofactor.dto;

/**
 * {@code EnrolamientoSegundoFactor} de ms-identidad-auth.yaml 2.2.0: el secreto
 * recien generado, la unica vez que sale del servidor.
 *
 * <p>{@link #toString()} lo tapa: un registro descuidado de este objeto no
 * puede dejarlo en la bitacora.
 */
public record EnrolamientoResponse(String secreto,
                                   String uriOtpauth,
                                   String emisor,
                                   String cuenta,
                                   String algoritmo,
                                   int digitos,
                                   int periodoSegundos) {

    @Override
    public String toString() {
        return "EnrolamientoResponse[emisor=" + emisor + ", algoritmo=" + algoritmo + ", digitos=" + digitos
                + ", periodoSegundos=" + periodoSegundos + "]";
    }
}
