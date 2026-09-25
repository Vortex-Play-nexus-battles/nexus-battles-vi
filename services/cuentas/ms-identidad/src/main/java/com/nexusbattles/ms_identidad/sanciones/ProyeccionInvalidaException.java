package com.nexusbattles.ms_identidad.sanciones;

/** 400 {@code datos-invalidos}: una proyeccion sin sancion, con un estado desconocido o una suspension sin fin. */
public class ProyeccionInvalidaException extends RuntimeException {

    public ProyeccionInvalidaException(String detalle) {
        super(detalle);
    }
}
