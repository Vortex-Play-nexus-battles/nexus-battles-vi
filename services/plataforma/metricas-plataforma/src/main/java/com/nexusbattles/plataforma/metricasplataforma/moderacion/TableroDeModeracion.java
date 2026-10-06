package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import java.util.ArrayList;
import java.util.List;

/**
 * Metricas de usuarios y moderacion — HU-MET-001 (RF-MET-001).
 *
 * <p>Lo que hay: sanciones por periodo, por tipo y por dia, apelaciones,
 * moderadores activos, revertidas (fuente: moderacion-sanciones) y las
 * cuentas por estado con las altas por dia (fuente: ms-identidad, ruta de
 * indicadores de HU-USR-008). Lo que NO hay, dicho por su nombre en
 * {@code pendientes} y no disimulado: la frecuencia de reportes (HU-COM-006
 * sin implementar) y, si identidad no respondio o nego el permiso, el
 * registro de nuevos usuarios con su motivo (CA-03: lo demas sigue publicado).
 *
 * <p>La alerta de «alta frecuencia» solo se evalua si el PO fijo el umbral
 * ({@code umbralSancionesPorDia}); sin el, {@code alertasConfiguradas=false}
 * y no se inventa ninguno (D-25). El umbral es «a partir de»: el PO lo fijo
 * en 3 el 2026-10-05, y un dia con 3 sanciones o mas ya es alta frecuencia.
 */
public record TableroDeModeracion(FuenteDeModeracion.Agregados sanciones, boolean alertasConfiguradas,
                                  Integer umbralSancionesPorDia, List<String> alertas, List<String> pendientes,
                                  FuenteDeUsuarios.Indicadores registroDeUsuarios) {

    public static final String PENDIENTE_REPORTES = "frecuencia de reportes: HU-COM-006 #523 sin implementar";

    /**
     * @param usuarios         indicadores de cuentas, o {@code null} si no se pudieron leer
     * @param motivoSinUsuarios por que no se pudieron leer; solo se usa si {@code usuarios} es {@code null}
     */
    public static TableroDeModeracion de(FuenteDeModeracion.Agregados agregados, Integer umbralSancionesPorDia,
                                         FuenteDeUsuarios.Indicadores usuarios, String motivoSinUsuarios) {
        List<String> alertas = new ArrayList<>();
        boolean configuradas = umbralSancionesPorDia != null && umbralSancionesPorDia > 0;
        if (configuradas) {
            // D-25: el umbral es «a partir de». Un dia con exactamente el umbral ya es alta frecuencia.
            agregados.porDia().stream()
                    .filter(d -> d.emitidas() >= umbralSancionesPorDia)
                    .forEach(d -> alertas.add("alta frecuencia de sanciones el " + d.fecha() + ": " + d.emitidas()
                            + " (umbral: " + umbralSancionesPorDia + " o mas por dia)"));
        }
        List<String> pendientes = new ArrayList<>();
        if (usuarios == null) {
            pendientes.add("registro de nuevos usuarios: " + motivoSinUsuarios);
        }
        pendientes.add(PENDIENTE_REPORTES);
        return new TableroDeModeracion(agregados, configuradas, configuradas ? umbralSancionesPorDia : null,
                List.copyOf(alertas), List.copyOf(pendientes), usuarios);
    }
}
