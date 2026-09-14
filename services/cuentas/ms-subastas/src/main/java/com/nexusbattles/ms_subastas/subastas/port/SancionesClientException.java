package com.nexusbattles.ms_subastas.subastas.port;

public class SancionesClientException extends RuntimeException {
    public SancionesClientException(String mensaje) { super(mensaje); }
    public SancionesClientException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
