package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Agregados de moderacion de un periodo — HU-MET-001 (RF-MET-001): sanciones
 * emitidas por tipo y por dia, apelaciones por estado, moderadores activos.
 * Solo cuentas: ningun identificador de jugador sale de aqui.
 */
public record MetricasDeModeracion(OffsetDateTime desde, OffsetDateTime hasta, long total,
                                   Map<Sancion.Tipo, Long> porTipo, List<PorDia> porDia,
                                   Map<Apelacion.Estado, Long> apelaciones, long moderadoresActivos,
                                   long revertidas) {

    public record PorDia(LocalDate fecha, long emitidas) { }

    public static MetricasDeModeracion de(OffsetDateTime desde, OffsetDateTime hasta, List<Sancion> sanciones,
                                          List<Apelacion> apelacionesDelPeriodo) {
        Map<Sancion.Tipo, Long> porTipo = new EnumMap<>(Sancion.Tipo.class);
        for (Sancion.Tipo tipo : Sancion.Tipo.values()) {
            porTipo.put(tipo, 0L);
        }
        Map<LocalDate, Long> dias = new TreeMap<>();
        Set<UUID> moderadores = new java.util.HashSet<>();
        long revertidas = 0;
        for (Sancion s : sanciones) {
            porTipo.merge(s.tipo(), 1L, Long::sum);
            dias.merge(s.emitidaEn().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate(), 1L, Long::sum);
            moderadores.add(s.emitidaPor());
            if (s.revertidaEn() != null) {
                revertidas++;
            }
        }
        Map<Apelacion.Estado, Long> porEstado = new EnumMap<>(Apelacion.Estado.class);
        for (Apelacion.Estado estado : Apelacion.Estado.values()) {
            porEstado.put(estado, 0L);
        }
        for (Apelacion a : apelacionesDelPeriodo) {
            porEstado.merge(a.estado(), 1L, Long::sum);
        }
        List<PorDia> porDia = dias.entrySet().stream()
                .map(e -> new PorDia(e.getKey(), e.getValue()))
                .collect(Collectors.toCollection(java.util.ArrayList::new));
        return new MetricasDeModeracion(desde, hasta, sanciones.size(), new LinkedHashMap<>(porTipo), porDia,
                new LinkedHashMap<>(porEstado), moderadores.size(), revertidas);
    }

    /** Sanciones emitidas el dia con mas actividad del periodo (para el umbral de alerta). */
    public long maximoEnUnDia() {
        return porDia.stream().mapToLong(PorDia::emitidas).max().orElse(0);
    }
}
