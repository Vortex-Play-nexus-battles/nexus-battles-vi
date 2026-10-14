package com.nexusbattles.ms_identidad.auth.exception;

import com.nexusbattles.ms_identidad.auth.segundofactor.DesafioEmitido;

/**
 * 403 {@code cuenta-baneada}. Desde SA-4 lleva, si se pudo emitir, el codigo
 * con el que la cuenta ve su baneo y lo apela sin sesion (§7.3.2: el baneo
 * solo se revierte por apelacion).
 */
public class CuentaBaneadaException extends RuntimeException {

    private final DesafioEmitido apelacion;

    public CuentaBaneadaException(String mensaje) {
        this(mensaje, null);
    }

    public CuentaBaneadaException(String mensaje, DesafioEmitido apelacion) {
        super(mensaje);
        this.apelacion = apelacion;
    }

    /** SA-4: el codigo de apelacion sin sesion; nulo si no hay. */
    public DesafioEmitido getApelacion() {
        return apelacion;
    }
}
