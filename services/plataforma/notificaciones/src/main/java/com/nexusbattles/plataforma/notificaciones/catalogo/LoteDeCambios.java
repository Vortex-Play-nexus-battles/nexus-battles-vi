package com.nexusbattles.plataforma.notificaciones.catalogo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Un lote de cambios del catalogo — esquema {@code LoteDeAlertasCatalogo} de
 * productos.yaml (1.6.0).
 *
 * @param hasta    el punto de lectura que se guarda y se envia como {@code desde} la vez siguiente
 * @param completo false si productos corto el lote y quedan cambios despues de {@code hasta}
 * @param alertas  del cambio mas antiguo al mas reciente
 */
public record LoteDeCambios(Instant hasta, boolean completo, List<CambioDelCatalogo> alertas) {

    public LoteDeCambios {
        Objects.requireNonNull(hasta, "hasta es obligatorio");
        alertas = List.copyOf(Objects.requireNonNull(alertas, "las alertas son obligatorias"));
    }
}
