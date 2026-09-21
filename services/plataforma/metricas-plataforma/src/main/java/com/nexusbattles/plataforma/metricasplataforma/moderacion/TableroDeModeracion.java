package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Metricas de usuarios y moderacion — HU-MET-001 (RF-MET-001).
 *
 * <p>Lo que hay: sanciones por periodo, por tipo y por dia, apelaciones,
 * moderadores activos, revertidas (fuente: moderacion-sanciones). Lo que
 * NO hay, dicho por su nombre en {@code pendientes} y no disimulado: el
 * registro de nuevos usuarios (vive en ms-identidad, Grupo 4, sin contrato
 * de lectura) y la frecuencia de reportes (HU-COM-006 sin implementar).
 *
 * <p>La alerta de «alta frecuencia» solo se evalua si el PO fijo el umbral
 * ({@code umbralSancionesPorDia}); sin el, {@code alertasConfiguradas=false}
 * y no se inventa ninguno (D-25).
 */
public record TableroDeModeracion(FuenteDeModeracion.Agregados sanciones, boolean alertasConfiguradas,
                                  Integer umbralSancionesPorDia, List<String> alertas, List<String> pendientes,
                                  Map<String, Object> registroDeUsuarios) {

    public static final List<String> PENDIENTES = List.of(
            "registro de nuevos usuarios: sin contrato de lectura en ms-identidad (HU-USR-008 #532, Grupo 4)",
            "frecuencia de reportes: HU-COM-006 #523 sin implementar");

    public static TableroDeModeracion de(FuenteDeModeracion.Agregados agregados, Integer umbralSancionesPorDia) {
        List<String> alertas = new ArrayList<>();
        boolean configuradas = umbralSancionesPorDia != null && umbralSancionesPorDia > 0;
        if (configuradas) {
            agregados.porDia().stream()
                    .filter(d -> d.emitidas() > umbralSancionesPorDia)
                    .forEach(d -> alertas.add("alta frecuencia de sanciones el " + d.fecha() + ": " + d.emitidas()
                            + " por encima de " + umbralSancionesPorDia));
        }
        return new TableroDeModeracion(agregados, configuradas, configuradas ? umbralSancionesPorDia : null,
                List.copyOf(alertas), PENDIENTES, null);
    }
}
