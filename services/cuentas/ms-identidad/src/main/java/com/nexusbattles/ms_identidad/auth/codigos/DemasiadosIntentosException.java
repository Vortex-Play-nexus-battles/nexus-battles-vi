package com.nexusbattles.ms_identidad.auth.codigos;

/**
 * 429 {@code demasiados-intentos}: el codigo se anulo al agotar sus intentos
 * fallidos ({@code identidad.codigos.intentos-maximos}, 5). La salida es pedir
 * uno nuevo; insistir con el mismo ya no sirve.
 */
public class DemasiadosIntentosException extends RuntimeException {

    public static final String MENSAJE =
            "Demasiados intentos fallidos: este código quedó anulado. Pide uno nuevo.";

    public DemasiadosIntentosException() {
        super(MENSAJE);
    }
}
