package com.nexusbattles.ms_identidad.admin.inventario;

/**
 * La cuenta es anterior al identificador publico ({@code uid}) y el inventario
 * solo guarda la propiedad por {@code uid}: no hay inventario al que entregar.
 */
public class CuentaSinInventarioException extends RuntimeException {

    public CuentaSinInventarioException(Long usuarioId) {
        super("La cuenta " + usuarioId + " no tiene identificador público, así que no tiene inventario.");
    }
}
