package com.nexusbattles.ms_subastas.subastas.service;

public class PublicacionSubastaException extends RuntimeException {
    public enum Motivo { SOLICITUD_INVALIDA, NO_AUTENTICADO, PROHIBIDO, NO_ENCONTRADO, CONFLICTO, REGLA_NEGOCIO, DEPENDENCIA_NO_DISPONIBLE }

    /** B8: el tope de 10 subastas activas por vendedor (7.7.10). */
    public static final String LIMITE_PUBLICACIONES_ACTIVAS = "LIMITE_PUBLICACIONES_ACTIVAS";
    /** B8: la compra inmediata no supera el precio minimo (7.7.2). */
    public static final String COMPRA_INMEDIATA_NO_SUPERIOR = "COMPRA_INMEDIATA_NO_SUPERIOR";
    /** B8: {@code subastas.incremento-minimo} sin valor en admin-parametros (decision del PO). */
    public static final String INCREMENTO_MINIMO_NO_CONFIGURADO = "INCREMENTO_MINIMO_NO_CONFIGURADO";

    private final Motivo motivo;

    /**
     * Codigo estable para la interfaz ({@code motivo} del problem+json), o nulo
     * en los errores que siguen identificandose por el texto (contrato 1.0.0 de
     * {@code ms-subastas-publicar.yaml}).
     */
    private final String codigo;

    public PublicacionSubastaException(String mensaje) { this(Motivo.REGLA_NEGOCIO, mensaje); }
    public PublicacionSubastaException(String mensaje, Throwable causa) { this(Motivo.REGLA_NEGOCIO, mensaje, causa); }
    public PublicacionSubastaException(Motivo motivo, String mensaje) { this(motivo, mensaje, (Throwable) null); }
    public PublicacionSubastaException(Motivo motivo, String mensaje, Throwable causa) {
        this(motivo, null, mensaje, causa);
    }
    public PublicacionSubastaException(Motivo motivo, String codigo, String mensaje) {
        this(motivo, codigo, mensaje, null);
    }
    public PublicacionSubastaException(Motivo motivo, String codigo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.motivo = motivo;
        this.codigo = codigo;
    }
    public Motivo getMotivo() { return motivo; }
    public String getCodigo() { return codigo; }
}
