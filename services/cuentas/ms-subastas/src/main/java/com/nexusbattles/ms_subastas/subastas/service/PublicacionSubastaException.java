package com.nexusbattles.ms_subastas.subastas.service;

public class PublicacionSubastaException extends RuntimeException {
    public PublicacionSubastaException(String mensaje) { super(mensaje); }
    public PublicacionSubastaException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
