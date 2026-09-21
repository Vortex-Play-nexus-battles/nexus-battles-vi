package com.nexusbattles.ms_subastas.subastas.port;

/**
 * Capacidad de inventario destinada a publicacion: consulta por uid, reserva
 * atomica y liberacion idempotente conforme al futuro contrato del proveedor.
 * No registrar una implementacion hasta que exista ese contrato real.
 * El fake y el cliente actual de pujas no ofrecen esta capacidad.
 */
public interface InventarioPublicacionClient extends InventarioClient { }
