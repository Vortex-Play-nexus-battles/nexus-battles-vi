package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

/**
 * Puerto de salida hacia el modulo de notificaciones — HU-NOT-005, CA-06:
 * solo este servicio, con su credencial de servicio, fabrica un aviso de
 * sancion.
 */
public interface EmisorDeAvisos {

    /** Resultado de un intento de entrega. */
    enum Resultado {
        /** Entregado (201) o ya lo tenia (409): en los dos casos esta hecho. */
        ENTREGADO,
        /** El modulo rechazo el aviso (4xx distinto de 409): hay que verlo, no reintentar a ciegas. */
        RECHAZADO
    }

    /**
     * @throws RuntimeException si el modulo no responde (se reintenta despues)
     */
    Resultado entregar(AvisoPendiente aviso);
}
