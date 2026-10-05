package com.nexusbattles.ms_subastas.notificaciones;

/** Correo rechazo el aviso por su forma (400): reintentarlo tal cual volveria a fallar. */
public class CorreoRechazadoException extends RuntimeException {

    public CorreoRechazadoException(String mensaje) {
        super(mensaje);
    }
}
