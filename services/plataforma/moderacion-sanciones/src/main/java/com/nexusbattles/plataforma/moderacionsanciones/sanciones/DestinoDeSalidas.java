package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

/**
 * Puerto de salida de un canal: entrega una {@link SalidaPendiente} a su
 * servicio de destino. Hay uno por {@link CanalDeSalida} (antes de B2 se
 * llamaba {@code EmisorDeAvisos} y solo existia el de notificaciones).
 *
 * <p>Tres desenlaces, y solo tres:
 * <ul>
 *   <li>{@link Resultado#ENTREGADO}: el destino la tiene (tambien si ya la
 *       tenia de un intento anterior: todos los destinos son idempotentes
 *       por el identificador o la clave de la salida);</li>
 *   <li>{@link Resultado#RECHAZADO}: el destino respondio y dijo que no (un
 *       4xx que reintentar a ciegas no arreglaria, como un uid que ms-identidad
 *       no conoce). Se queda pendiente, con el motivo a la vista y la espera
 *       mas larga, para que alguien lo mire; nunca se descarta;</li>
 *   <li>una excepcion: el destino no respondio (caido, lento, sin credencial
 *       de servicio). Se reintenta despues.</li>
 * </ul>
 */
public interface DestinoDeSalidas {

    /** Resultado de un intento de entrega. */
    enum Resultado {
        /** Entregado (o ya lo tenia): esta hecho. */
        ENTREGADO,
        /** El destino la rechazo: hay que verlo, no reintentar a ciegas. */
        RECHAZADO
    }

    /** El canal que atiende este destino. */
    CanalDeSalida canal();

    /**
     * @throws RuntimeException si el destino no responde (se reintenta despues)
     */
    Resultado entregar(SalidaPendiente salida);
}
