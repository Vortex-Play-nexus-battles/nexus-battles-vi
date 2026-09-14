package com.nexusbattles.ms_subastas.subastas.port;

/**
 * Inventario no pudo responder: se cayo la conexion, vencio el tiempo de espera
 * o devolvio 503.
 *
 * <p>Existe separada del {@link InventarioClientException} de negocio para que
 * el cortacircuitos y el reintento puedan distinguirlas. Un 409 ("ese elemento
 * ya esta bloqueado por otra subasta") es una respuesta correcta del servicio y
 * reintentarla da exactamente lo mismo, ademas de empujar el cortacircuitos
 * hacia abierto por algo que no es una averia. Esto, en cambio, si merece
 * reintento y si cuenta como fallo del servicio.
 */
public class InventarioNoDisponibleException extends InventarioClientException {

    public InventarioNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public InventarioNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
