package com.nexusbattles.ms_ecommerce.compra;

/**
 * La orden no existe, o es de otro jugador: 404 {@code orden-inexistente}.
 * Las dos cosas responden igual para no confirmar que un id de orden ajeno
 * existe.
 */
public class OrdenInexistenteException extends RuntimeException {

    public OrdenInexistenteException() {
        super("No tienes ninguna orden con ese identificador.");
    }
}
