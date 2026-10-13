package com.nexusbattles.ms_identidad.auth.validation;

/**
 * El apodo coincide con la lista negra (HU-ADM-002). Sigue siendo una
 * {@link IllegalArgumentException} para que los flujos que ya la capturaban
 * (registro, alta y edicion de administracion) no cambien; el cambio de apodo
 * desde «Mi cuenta» la distingue para responder un problem details con su
 * {@code type} propio (RFINAL-03): hasta hoy salia como texto plano y la vista
 * solo podia decir «Revisa los datos e inténtalo otra vez».
 */
public class ApodoNoPermitidoException extends IllegalArgumentException {

    public ApodoNoPermitidoException(String motivo) {
        super(motivo);
    }
}
