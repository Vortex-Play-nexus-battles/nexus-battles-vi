package com.nexusbattles.ms_identidad.auth.exception;

public class CuentaSuspendidaException extends RuntimeException {
    public CuentaSuspendidaException(String mensaje) {
        super(mensaje);
    }
}
