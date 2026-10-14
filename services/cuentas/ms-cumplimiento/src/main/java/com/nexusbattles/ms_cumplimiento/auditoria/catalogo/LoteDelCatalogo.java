package com.nexusbattles.ms_cumplimiento.auditoria.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * Un lote de {@code GET /api/v1/productos/auditoria/operaciones}
 * (productos.yaml 1.12.0, esquema {@code LoteDeOperacionesAdministrativas}).
 *
 * @param hasta       se envía como {@code desde} para pedir lo que sigue
 * @param completo    false si quedaron operaciones por entregar
 * @param operaciones de la más antigua a la más reciente
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoteDelCatalogo(Instant hasta, Boolean completo, List<OperacionDelCatalogo> operaciones) {

    /** Sin el campo, se da por completo: lo contrario pediría lotes sin fin. */
    public boolean estaCompleto() {
        return !Boolean.FALSE.equals(completo);
    }
}
