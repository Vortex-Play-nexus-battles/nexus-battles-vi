package com.nexusbattles.ms_identidad.privacidad;

/**
 * ms-subastas no respondio, respondio un error o algo que no se entiende: no
 * se sabe si la persona tiene subastas o pujas abiertas.
 */
public class SubastasNoDisponiblesException extends RuntimeException {

    public SubastasNoDisponiblesException(String mensaje) {
        super(mensaje);
    }

    public SubastasNoDisponiblesException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
