package com.nexusbattles.plataforma.notificaciones.politicas;

import java.time.Instant;

/**
 * Lo que notificaciones necesita de admin-parametros para §7.3.7 (SA-11):
 * los cambios en las politicas de uso publicados despues de un punto
 * ({@code GET /api/v1/parametros/politicas/cambios/posteriores},
 * admin-parametros.yaml 1.1.0).
 */
@FunctionalInterface
public interface CambiosDePolitica {

    /**
     * @param desde el punto de lectura; se devuelve lo publicado estrictamente despues
     * @return el lote, nunca {@code null}
     * @throws PoliticasNoDisponibles si admin-parametros no responde o responde algo que no es un lote
     */
    LoteDeCambiosDePolitica consultar(Instant desde);
}
