package com.nexusbattles.ms_identidad.admin.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/v1/admin/jugadores/indicadores} — HU-USR-008 (#561),
 * esquema {@code IndicadoresDeCuentas} de ms-identidad-admin.yaml 1.3.0.
 *
 * @param total          cuentas que hay ahora (la suma de {@code porEstado})
 * @param porEstado      cuentas en cada estado AHORA, con los cinco del contrato
 *                       siempre presentes (0 si no hay ninguna) y en su orden
 * @param registros      altas por dia en el rango pedido
 * @param ocultarPruebas si se dejaron fuera las cuentas de pruebas automaticas
 * @param calculadoEn    cuando se contaron
 */
public record IndicadoresDeCuentasResponse(
        long total,
        Map<String, Long> porEstado,
        RegistrosPorDia registros,
        boolean ocultarPruebas,
        OffsetDateTime calculadoEn) {

    /**
     * @param desde   primer dia de la serie (inclusive)
     * @param hasta   ultimo dia de la serie (inclusive)
     * @param total   altas en el rango (la suma de {@code porDia})
     * @param porDia  un elemento por dia, en orden, con 0 en los dias sin altas
     */
    public record RegistrosPorDia(LocalDate desde, LocalDate hasta, long total, List<RegistrosDelDia> porDia) {
    }

    /** Altas de cuentas de un dia. */
    public record RegistrosDelDia(LocalDate fecha, long cuentas) {
    }
}
