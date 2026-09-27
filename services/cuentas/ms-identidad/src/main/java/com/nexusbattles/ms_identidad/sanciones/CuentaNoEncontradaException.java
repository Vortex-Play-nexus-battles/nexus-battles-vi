package com.nexusbattles.ms_identidad.sanciones;

/** 404 {@code cuenta-no-encontrada} en las rutas internas: no existe una cuenta con ese uid. */
public class CuentaNoEncontradaException extends RuntimeException {

    public CuentaNoEncontradaException() {
        super("No existe una cuenta con ese identificador.");
    }
}
