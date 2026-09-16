package com.nexusbattles.ms_subastas.pujas.service;

/**
 * Rechazo de negocio al participar en una subasta. El {@link Motivo} es un
 * codigo estable: viaja en el campo {@code motivo} del problem+json para que
 * la interfaz elija el mensaje sin leer el texto libre, que puede cambiar.
 *
 * <p>Los motivos estan declarados en {@code ms-subastas-pujas.yaml}; anadir uno
 * aqui obliga a anadirlo alli.
 */
public class PujaRechazadaException extends RuntimeException {

    public enum Motivo {
        SUBASTA_NO_ACTIVA,
        PUJA_PROPIA,
        OFERTA_INSUFICIENTE,
        INTERVALO_MINIMO_NO_CUMPLIDO,
        LIMITE_SUBASTAS_ACTIVAS,
        LIMITE_PUJAS_ACTIVAS,
        LIMITE_AUTOMATICO_INALCANZABLE,
        SALDO_INSUFICIENTE_PARA_LIMITE,

        /**
         * La subasta se publico sin precio de compra inmediata. Antes era un
         * IllegalStateException, que por HTTP se habria visto como un 500: es
         * una situacion legitima del cliente (la interfaz ofrecio el boton
         * sobre una subasta que no lo admite), no un fallo del servidor.
         */
        SIN_COMPRA_INMEDIATA,

        /**
         * Llego una compra inmediata con {@code confirmado} en false. La
         * historia exige confirmacion explicita, asi que el servidor la exige
         * tambien: una interfaz con un bug no debe poder cerrar una compra.
         */
        CONFIRMACION_REQUERIDA,

        /**
         * La misma Idempotency-Key llego antes para otra subasta u otro
         * jugador. No se reproduce la puja original —seria devolverle a alguien
         * una puja que no es suya— ni se registra una nueva, porque la clave ya
         * no identifica una sola operacion. Es un error del cliente al generar
         * la clave, no una carrera: reintentar tal cual volveria a fallar.
         */
        CLAVE_REUTILIZADA
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
