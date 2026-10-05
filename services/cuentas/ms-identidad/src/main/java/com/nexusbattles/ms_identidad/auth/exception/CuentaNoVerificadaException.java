package com.nexusbattles.ms_identidad.auth.exception;

/**
 * 403 {@code cuenta-no-verificada} (B1): la contrasena es correcta pero el
 * correo aun no se confirmo con su codigo. Solo sale con la contrasena
 * correcta; con una incorrecta, el 401 generico de siempre.
 */
public class CuentaNoVerificadaException extends RuntimeException {

    public static final String MENSAJE =
            "Tu correo aún no está verificado. Escribe el código que te enviamos o pide uno nuevo.";

    public CuentaNoVerificadaException() {
        super(MENSAJE);
    }
}
