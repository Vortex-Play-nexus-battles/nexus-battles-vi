package com.nexusbattles.ms_subastas.subastas.service;

public class PublicacionSubastaException extends RuntimeException {
    public enum Motivo { SOLICITUD_INVALIDA, NO_AUTENTICADO, PROHIBIDO, NO_ENCONTRADO, CONFLICTO, REGLA_NEGOCIO, DEPENDENCIA_NO_DISPONIBLE }
    private final Motivo motivo;
    public PublicacionSubastaException(String mensaje) { this(Motivo.REGLA_NEGOCIO, mensaje); }
    public PublicacionSubastaException(String mensaje, Throwable causa) { this(Motivo.REGLA_NEGOCIO, mensaje, causa); }
    public PublicacionSubastaException(Motivo motivo, String mensaje) { this(motivo, mensaje, null); }
    public PublicacionSubastaException(Motivo motivo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.motivo = motivo;
    }
    public Motivo getMotivo() { return motivo; }
}
