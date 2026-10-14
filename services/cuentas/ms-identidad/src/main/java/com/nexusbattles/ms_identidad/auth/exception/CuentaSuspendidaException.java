package com.nexusbattles.ms_identidad.auth.exception;

import com.nexusbattles.ms_identidad.auth.segundofactor.DesafioEmitido;

import java.time.OffsetDateTime;

/**
 * 403 {@code cuenta-suspendida}. Desde B2 lleva el fin de la suspension: el
 * 7.3.2 pide un «contador visible del tiempo restante», y la interfaz lo
 * calcula con {@code suspendidoHasta} del problem details en vez de leer el
 * texto.
 */
public class CuentaSuspendidaException extends RuntimeException {

    private final OffsetDateTime hasta;
    private final DesafioEmitido apelacion;

    public CuentaSuspendidaException(String mensaje) {
        this(mensaje, null);
    }

    public CuentaSuspendidaException(String mensaje, OffsetDateTime hasta) {
        this(mensaje, hasta, null);
    }

    /** SA-4: con el codigo para apelar sin sesion (§7.3.2), si se pudo emitir. */
    public CuentaSuspendidaException(String mensaje, OffsetDateTime hasta, DesafioEmitido apelacion) {
        super(mensaje);
        this.hasta = hasta;
        this.apelacion = apelacion;
    }

    /** Fin de la suspension, con zona; nulo si no se conoce. */
    public OffsetDateTime getHasta() {
        return hasta;
    }

    /** SA-4: el codigo de apelacion sin sesion; nulo si no hay. */
    public DesafioEmitido getApelacion() {
        return apelacion;
    }
}
