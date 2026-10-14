package com.nexusbattles.ms_identidad.admin.inventario;

/**
 * El inventario no respondio (5xx, tiempo agotado, conexion rechazada o una
 * respuesta sin la entrega). Se responde 503 con {@code Retry-After}: repetir
 * con la misma clave termina la entrega sin duplicarla.
 */
public class InventarioNoDisponibleException extends RuntimeException {

    public static final int REINTENTAR_EN_SEGUNDOS = 30;

    public InventarioNoDisponibleException(String detalle, Throwable causa) {
        super(detalle, causa);
    }
}
