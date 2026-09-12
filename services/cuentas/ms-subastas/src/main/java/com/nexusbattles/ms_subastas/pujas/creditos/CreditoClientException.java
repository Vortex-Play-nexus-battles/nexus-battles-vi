package com.nexusbattles.ms_subastas.pujas.creditos;

public class CreditoClientException extends RuntimeException {

    public enum Motivo {
        SALDO_INSUFICIENTE,
        RESERVA_INEXISTENTE,
        RESERVA_YA_LIBERADA,
        RESERVA_YA_CONSUMIDA
    }

    private final Motivo motivo;

    public CreditoClientException(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
