package com.nexusbattles.ms_subastas.subastas.port;

import java.util.UUID;
import java.util.Optional;

public interface InventarioClient {
    Optional<ElementoInventario> buscar(String elementoInventarioId);
    void reservar(String elementoInventarioId, UUID propietarioId, UUID subastaId, String idempotencyKey);
    void liberarReserva(String elementoInventarioId, UUID subastaId, String idempotencyKey);

    record ElementoInventario(String id, UUID productoId, UUID propietarioId, boolean enUso) { }
}
