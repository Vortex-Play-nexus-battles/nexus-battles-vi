package com.nexusbattles.ms_identidad.auth.exception;

import java.time.OffsetDateTime;

/**
 * 403 {@code cuenta-suspendida}. Desde B2 lleva el fin de la suspension: el
 * 7.3.2 pide un «contador visible del tiempo restante», y la interfaz lo
 * calcula con {@code suspendidoHasta} del problem details en vez de leer el
 * texto.
 */
public class CuentaSuspendidaException extends RuntimeException {

    private final OffsetDateTime hasta;

    public CuentaSuspendidaException(String mensaje) {
        this(mensaje, null);
    }

    public CuentaSuspendidaException(String mensaje, OffsetDateTime hasta) {
        super(mensaje);
        this.hasta = hasta;
    }

    /** Fin de la suspension, con zona; nulo si no se conoce. */
    public OffsetDateTime getHasta() {
        return hasta;
    }
}
