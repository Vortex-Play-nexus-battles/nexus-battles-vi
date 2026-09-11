package com.nexusbattles.ms_subastas.pujas.service;

public class PujaRechazadaException extends RuntimeException {

    public enum Motivo {
        SUBASTA_NO_ACTIVA,
        PUJA_PROPIA,
        OFERTA_INSUFICIENTE,
        INTERVALO_MINIMO_NO_CUMPLIDO,
        LIMITE_SUBASTAS_ACTIVAS,
        LIMITE_PUJAS_ACTIVAS
    }

    private final Motivo motivo;

    public PujaRechazadaException(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
