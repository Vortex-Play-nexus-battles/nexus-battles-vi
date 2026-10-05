package com.nexusbattles.ms_subastas.notificaciones;

/** Correo o ms-identidad no respondieron, o no aceptaron la credencial: el correo se reintenta mas tarde. */
public class CorreoNoDisponibleException extends RuntimeException {

    public CorreoNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public CorreoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
