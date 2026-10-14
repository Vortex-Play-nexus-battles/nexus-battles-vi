package com.nexusbattles.plataforma.notificaciones.politicas;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Un lote de cambios en las politicas de uso — esquema
 * {@code LoteDeCambiosDePolitica} de admin-parametros.yaml (1.1.0).
 *
 * @param hasta    el punto de lectura que se guarda para la consulta siguiente
 * @param completo false si admin-parametros corto el lote y hay mas despues de {@code hasta}
 * @param cambios  del mas antiguo al mas reciente
 */
public record LoteDeCambiosDePolitica(Instant hasta, boolean completo, List<CambioDePoliticaDeUso> cambios) {

    public LoteDeCambiosDePolitica {
        Objects.requireNonNull(hasta, "hasta es obligatorio");
        cambios = List.copyOf(Objects.requireNonNull(cambios, "los cambios son obligatorios"));
    }
}
