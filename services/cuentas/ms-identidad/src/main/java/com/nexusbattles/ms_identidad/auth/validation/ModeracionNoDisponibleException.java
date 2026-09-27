package com.nexusbattles.ms_identidad.auth.validation;

/**
 * moderacion-sanciones no respondio (error, tiempo agotado o circuito
 * abierto) cuando hacia falta su respuesta para decidir: se responde 503
 * {@code moderacion-no-disponible} con {@code Retry-After}, nunca se da por
 * bueno lo que no se pudo comprobar.
 *
 * <p>Dos usos (B2): la lista negra del apodo (registro, alta administrativa,
 * cambio de apodo) y la delegacion de sanciones del panel. Antes de B2 la
 * lista negra fallaba hacia el lado abierto: con moderacion caida se aceptaba
 * cualquier apodo, que es una de las formas en que «spiderman» entro.
 *
 * <p>No extiende {@code IllegalArgumentException} ni
 * {@code IllegalStateException} a proposito: los controladores heredados las
 * convierten en 400 y 404, y esto no es ni un dato invalido ni algo que no
 * exista.
 */
public class ModeracionNoDisponibleException extends RuntimeException {

    /** Segundos sugeridos en {@code Retry-After}. */
    public static final int REINTENTAR_EN_SEGUNDOS = 30;

    public ModeracionNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public ModeracionNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
