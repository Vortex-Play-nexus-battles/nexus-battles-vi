package com.nexusbattles.ms_ecommerce.service;

/**
 * La linea no esta en el carrito de quien la pide: 404
 * {@code linea-inexistente}. Tambien si es de otro jugador: el carrito de otro
 * no existe para quien no es su dueno.
 */
public class LineaInexistenteException extends RuntimeException {

    public LineaInexistenteException() {
        super("Esa linea no esta en tu carrito.");
    }
}
