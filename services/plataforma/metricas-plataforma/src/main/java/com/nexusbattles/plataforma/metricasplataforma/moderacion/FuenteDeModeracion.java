package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import java.time.OffsetDateTime;
import java.util.Map;

/** Puerto hacia moderacion-sanciones: {@code GET /sanciones/metricas} (consulta 1.2.0). */
public interface FuenteDeModeracion {

    /** Forma exacta del contrato; el proveedor es quien agrega, aqui no se recalcula nada. */
    record Agregados(OffsetDateTime desde, OffsetDateTime hasta, long total, Map<String, Long> porTipo,
                     java.util.List<PorDia> porDia, Map<String, Long> apelaciones, long moderadoresActivos,
                     long revertidas) {
        public long maximoEnUnDia() {
            return porDia == null ? 0 : porDia.stream().mapToLong(PorDia::emitidas).max().orElse(0);
        }
    }

    record PorDia(String fecha, long emitidas) { }

    /** @throws FuenteNoDisponible si moderacion-sanciones no responde (CA-03: se informa, no se disimula) */
    Agregados consultar(OffsetDateTime desde, OffsetDateTime hasta);

    class FuenteNoDisponible extends RuntimeException {
        public FuenteNoDisponible(String detalle) {
            super(detalle);
        }
    }
}
