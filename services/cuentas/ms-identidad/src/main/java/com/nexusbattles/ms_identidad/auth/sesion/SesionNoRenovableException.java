package com.nexusbattles.ms_identidad.auth.sesion;

/** La sesion no se puede renovar: 401 {@code sesion-no-renovable}; la interfaz vuelve al login. */
public class SesionNoRenovableException extends RuntimeException {

    public SesionNoRenovableException(String detalle) {
        super(detalle);
    }
}
