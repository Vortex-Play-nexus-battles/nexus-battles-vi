package com.nexusbattles.ms_identidad.auth.exception;

public class CuentaBloqueadaException extends RuntimeException {
    public CuentaBloqueadaException(String mensaje) {
        super(mensaje);
    }
}
