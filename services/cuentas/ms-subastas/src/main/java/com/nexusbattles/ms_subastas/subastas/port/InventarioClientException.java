package com.nexusbattles.ms_subastas.subastas.port;

public class InventarioClientException extends RuntimeException {
    public InventarioClientException(String message) {
        super(message);
    }

    public InventarioClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
