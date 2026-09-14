package com.nexusbattles.ms_subastas.notificaciones;

/** El aviso no se pudo entregar. La fila del outbox se queda sin marcar y se reintenta. */
public class NotificacionesClientException extends RuntimeException {

    public NotificacionesClientException(String mensaje) {
        super(mensaje);
    }

    public NotificacionesClientException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
