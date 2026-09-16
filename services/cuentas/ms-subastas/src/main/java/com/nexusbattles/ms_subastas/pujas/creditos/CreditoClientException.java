package com.nexusbattles.ms_subastas.pujas.creditos;

public class CreditoClientException extends RuntimeException {

    public enum Motivo {
        SALDO_INSUFICIENTE,
        RESERVA_INEXISTENTE,
        RESERVA_YA_LIBERADA,
        RESERVA_YA_CONSUMIDA,

        /**
         * ms-finanzas no respondio: conexion caida, tiempo agotado o 5xx.
         * Es la unica que merece reintento y la unica que debe empujar el
         * cortacircuitos; los demas motivos son respuestas correctas del
         * servicio y reintentarlas da lo mismo.
         */
        SERVICIO_NO_DISPONIBLE,

        /** Respuesta que no encaja con el contrato: 4xx inesperado o cuerpo ilegible. */
        RESPUESTA_INESPERADA
    }

    private final Motivo motivo;

    public CreditoClientException(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = motivo;
    }

    public CreditoClientException(Motivo motivo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
