package com.nexusbattles.ms_subastas.panel.service;

/**
 * Rechazo de negocio en el panel personal (cancelar, seguir, recoger). El
 * {@link Motivo} viaja en el {@code motivo} del problem+json
 * ({@code ms-subastas-panel.yaml}); cada motivo sabe con que codigo HTTP sale.
 */
public class OperacionRechazadaException extends RuntimeException {

    public enum Motivo {
        /** Cancelar una subasta ajena. */
        NO_ES_EL_VENDEDOR(403),
        /** La subasta ya termino (o otro la cerro mientras tanto). */
        SUBASTA_NO_ACTIVA(409),
        /** 7.7.10: «Posible solo si no hay pujas registradas». Carrera: alguien pujo. */
        CANCELACION_CON_PUJAS(409),
        /** 7.7.10: «No permitida en las ultimas 6 horas de la subasta». */
        CANCELACION_FUERA_DE_PLAZO(422),
        /** Sin creditos para la penalizacion del 50 % de la comision. */
        SALDO_INSUFICIENTE(422),
        /** No hay un pendiente de esa subasta para este jugador (ni se dice si es de otro). */
        PENDIENTE_NO_ENCONTRADO(404),
        /** El plazo vencio y ya se aplico la politica del parametro. */
        PENDIENTE_YA_RESUELTO(409);

        private final int estadoHttp;

        Motivo(int estadoHttp) {
            this.estadoHttp = estadoHttp;
        }

        public int estadoHttp() {
            return estadoHttp;
        }
    }

    private final Motivo motivo;

    public OperacionRechazadaException(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
