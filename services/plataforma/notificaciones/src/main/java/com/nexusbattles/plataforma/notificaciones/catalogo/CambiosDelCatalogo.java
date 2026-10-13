package com.nexusbattles.plataforma.notificaciones.catalogo;

import java.time.Instant;

/**
 * Lo que notificaciones necesita de productos para HU-NOT-001: leer los
 * cambios del catalogo desde un punto de lectura
 * ({@code GET /api/v1/productos/alertas/cambios}, productos.yaml 1.6.0).
 */
@FunctionalInterface
public interface CambiosDelCatalogo {

    /**
     * @param desde el {@code hasta} del lote anterior; {@code null} pide la linea base
     * @return el lote, nunca {@code null}
     * @throws CatalogoNoDisponible si productos no responde o responde algo que no es un lote
     */
    LoteDeCambios consultar(Instant desde);
}
