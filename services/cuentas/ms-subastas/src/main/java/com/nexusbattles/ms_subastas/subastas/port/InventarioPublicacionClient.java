package com.nexusbattles.ms_subastas.subastas.port;

/**
 * Capacidad de inventario destinada a publicacion: consulta por uid, reserva
 * atomica y liberacion idempotente conforme a contracts/openapi/inventario.yaml.
 * El cliente HTTP compartido con pujas ofrece esta capacidad; el fake no.
 */
public interface InventarioPublicacionClient extends InventarioClient { }
