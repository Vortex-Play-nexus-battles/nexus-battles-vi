package com.nexusbattles.ms_identidad.auth.codigos;

/**
 * 400 {@code codigo-invalido}: no hay cuenta con ese correo, no tiene un
 * codigo vigente de ese tipo, el codigo no coincide, caduco o ya se uso.
 *
 * <p>Un solo mensaje para todos los casos a proposito: distinguirlos diria
 * que correos estan registrados y en que punto del flujo esta cada cuenta.
 */
public class CodigoInvalidoException extends RuntimeException {

    public static final String MENSAJE =
            "El código no es válido o ya no está vigente. Revisa el último correo o pide uno nuevo.";

    public CodigoInvalidoException() {
        super(MENSAJE);
    }
}
