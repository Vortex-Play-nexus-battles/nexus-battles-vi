package com.nexusbattles.plataforma.comentarios.moderacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * La frecuencia de reportes de un periodo — §7.3.8 «Alertas de actividad
 * sospechosa o alta frecuencia de reportes» y RF-MET-001 (comentarios.yaml
 * 1.15.0).
 *
 * <p>Hasta aqui los reportes solo se leian comentario por comentario (la cola)
 * o persona por persona (la ficha), y el tablero de moderacion decia «la
 * frecuencia de reportes no esta disponible». Aqui se cuentan todos los de un
 * periodo: en total, por dia, por categoria, cuantos comentarios distintos y
 * cuantas personas distintas. Solo cuentas: ningun identificador sale.
 *
 * <p><b>Dias UTC, a proposito.</b> Es el corte que ya usan las sanciones por
 * dia ({@code GET /sanciones/metricas}); el tablero pone las dos series una
 * junto a otra y las compara con su umbral dia por dia, asi que tienen que
 * partir el tiempo en el mismo sitio.
 *
 * <p>El umbral de «alta frecuencia» no vive aqui: es del tablero
 * (metricas-plataforma) y del Product Owner (D-25). Este servicio publica la
 * cifra; quien la juzga es otro.
 */
@Component
public class FrecuenciaDeReportes {

    /** Sin periodo, el mismo que el tablero de moderacion: los ultimos 30 dias. */
    static final Duration PERIODO_POR_OMISION = Duration.ofDays(30);

    /** Un año (bisiesto incluido) de una vez; mas que eso no es un tablero sino una exportacion. */
    static final Duration PERIODO_MAXIMO = Duration.ofDays(366);

    private final ReporteRepository reportes;
    private final Clock reloj;

    public FrecuenciaDeReportes(ReporteRepository reportes, Clock reloj) {
        this.reportes = reportes;
        this.reloj = reloj;
    }

    /** Reportes de un dia UTC con alguno. */
    public record PorDia(LocalDate fecha, long reportes) {
    }

    /**
     * La frecuencia de un periodo [desde, hasta).
     *
     * @param porCategoria las seis categorias de RF-COM-006, siempre, en el orden de la ficha
     */
    public record Frecuencia(Instant desde, Instant hasta, long total, List<PorDia> porDia,
            Map<CategoriaDeReporte, Long> porCategoria, long comentariosReportados, long reportantes) {
    }

    /**
     * @param desde desde ese instante (incluido); sin el, {@link #PERIODO_POR_OMISION} antes de {@code hasta}
     * @param hasta antes de ese instante (excluido); sin el, ahora
     * @throws IllegalArgumentException si el periodo esta al reves o pasa de {@link #PERIODO_MAXIMO}
     */
    @Transactional(readOnly = true)
    public Frecuencia consultar(Instant desde, Instant hasta) {
        Instant fin = hasta == null ? reloj.instant() : hasta;
        Instant inicio = desde == null ? fin.minus(PERIODO_POR_OMISION) : desde;
        if (!inicio.isBefore(fin)) {
            throw new IllegalArgumentException("«desde» tiene que ser anterior a «hasta»");
        }
        if (Duration.between(inicio, fin).compareTo(PERIODO_MAXIMO) > 0) {
            throw new IllegalArgumentException("Se pueden pedir como mucho 366 días de reportes a la vez.");
        }

        Map<CategoriaDeReporte, Long> porCategoria = new EnumMap<>(CategoriaDeReporte.class);
        for (CategoriaDeReporte categoria : CategoriaDeReporte.values()) {
            porCategoria.put(categoria, 0L);
        }
        Map<LocalDate, Long> dias = new TreeMap<>();
        Set<String> comentarios = new HashSet<>();
        Set<String> personas = new HashSet<>();
        List<ReporteContado> delPeriodo = reportes.contablesEntre(inicio, fin);
        for (ReporteContado reporte : delPeriodo) {
            porCategoria.merge(reporte.categoria(), 1L, Long::sum);
            dias.merge(LocalDate.ofInstant(reporte.fecha(), ZoneOffset.UTC), 1L, Long::sum);
            comentarios.add(reporte.comentarioId());
            personas.add(reporte.reportanteId());
        }
        List<PorDia> porDia = dias.entrySet().stream().map(e -> new PorDia(e.getKey(), e.getValue())).toList();
        return new Frecuencia(inicio, fin, delPeriodo.size(), porDia, porCategoria, comentarios.size(),
                personas.size());
    }
}
