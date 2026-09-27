package com.nexusbattles.ms_subastas.notificaciones;

/**
 * El aviso no se pudo entregar. La fila del outbox se queda sin marcar y se
 * reintenta, salvo que el fallo sea {@linkplain #esDefinitivo() definitivo}.
 */
public class NotificacionesClientException extends RuntimeException {

    private final boolean definitivo;

    public NotificacionesClientException(String mensaje) {
        this(mensaje, false);
    }

    public NotificacionesClientException(String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.definitivo = false;
    }

    /**
     * @param definitivo el modulo de notificaciones rechazo el aviso por su
     *                   forma (4xx distinto de 409, 401 y 403): reintentarlo
     *                   tal cual volveria a fallar, y dejarlo en la cola la
     *                   bloquearia entera (B8)
     */
    public NotificacionesClientException(String mensaje, boolean definitivo) {
        super(mensaje);
        this.definitivo = definitivo;
    }

    public boolean esDefinitivo() {
        return definitivo;
    }
}
