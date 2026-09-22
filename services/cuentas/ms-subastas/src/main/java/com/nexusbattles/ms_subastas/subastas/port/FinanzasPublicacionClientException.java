package com.nexusbattles.ms_subastas.subastas.port;

import com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException;

public class FinanzasPublicacionClientException extends PublicacionSubastaException {
    private final boolean resultadoIncierto;
    public FinanzasPublicacionClientException(String message) { this(message, null, false); }
    public FinanzasPublicacionClientException(String message, Throwable cause) { this(message, cause, false); }
    public FinanzasPublicacionClientException(String message, Throwable cause, boolean resultadoIncierto) {
        super(Motivo.DEPENDENCIA_NO_DISPONIBLE, message, cause);
        this.resultadoIncierto = resultadoIncierto;
    }
    public boolean resultadoIncierto() { return resultadoIncierto; }
}
