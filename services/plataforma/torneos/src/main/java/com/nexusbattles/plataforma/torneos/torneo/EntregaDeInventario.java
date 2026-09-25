package com.nexusbattles.plataforma.torneos.torneo;

import java.util.UUID;

/**
 * Puerto hacia inventario (inventario.yaml 1.4.0,
 * {@code POST /api/v1/inventario/entregas}): la unica via por la que torneos da
 * a un jugador la propiedad de un producto. Torneos nunca escribe el inventario
 * de nadie por su cuenta (regla 7).
 */
public interface EntregaDeInventario {

    /**
     * Entrega una unidad de {@code productoId} con origen PREMIO_TORNEO.
     * Idempotente por {@code claveIdempotente}: repetirla con el mismo cuerpo
     * devuelve la entrega original y nunca duplica el elemento.
     *
     * @throws FalloDeIntegracion si no se pudo
     */
    void entregarEpica(UUID jugador, String productoId, String referencia, String claveIdempotente);
}
