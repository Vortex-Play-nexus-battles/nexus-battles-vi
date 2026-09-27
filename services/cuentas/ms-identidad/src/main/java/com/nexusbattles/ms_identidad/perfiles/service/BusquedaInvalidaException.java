package com.nexusbattles.ms_identidad.perfiles.service;

/**
 * El texto de una busqueda de jugadores no se puede buscar: vacio, de menos
 * de {@value BusquedaDeJugadores#MINIMO_CARACTERES} caracteres o mas largo
 * que cualquier apodo. Se responde 400 {@code datos-invalidos}; el mensaje es
 * para la persona que escribe.
 */
public class BusquedaInvalidaException extends RuntimeException {

    public BusquedaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
