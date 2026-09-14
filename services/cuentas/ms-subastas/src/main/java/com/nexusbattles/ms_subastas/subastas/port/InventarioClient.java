package com.nexusbattles.ms_subastas.subastas.port;

import java.util.UUID;
import java.util.Optional;

public interface InventarioClient {
    Optional<ElementoInventario> buscar(String elementoInventarioId);
    void reservar(String elementoInventarioId, UUID propietarioId, UUID subastaId, String idempotencyKey);
    void liberarReserva(String elementoInventarioId, UUID subastaId, String idempotencyKey);

    /**
     * Transfiere formalmente un ítem de inventario al nuevo propietario tras la conclusión
     * de una subasta (por adjudicación al mejor postor o por compra inmediata).
     *
     * @param elementoInventarioId Identificador de la instancia del ítem en inventario.
     * @param nuevoPropietarioId Identificador del jugador adjudicado o comprador.
     * @param subastaId Identificador de la subasta concluida.
     * @param idempotencyKey Clave de idempotencia para garantizar entrega única.
     */
    void transferirProducto(String elementoInventarioId, UUID nuevoPropietarioId, UUID subastaId, String idempotencyKey);

    record ElementoInventario(String id, UUID productoId, UUID propietarioId, boolean enUso) { }
}
