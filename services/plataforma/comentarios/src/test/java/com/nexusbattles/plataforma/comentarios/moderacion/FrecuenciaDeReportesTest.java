package com.nexusbattles.plataforma.comentarios.moderacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("La frecuencia de reportes de un periodo (§7.3.8)")
class FrecuenciaDeReportesTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");

    private final ReporteRepository repositorio = mock(ReporteRepository.class);
    private final FrecuenciaDeReportes frecuencia =
            new FrecuenciaDeReportes(repositorio, Clock.fixed(AHORA, ZoneOffset.UTC));

    private static ReporteContado reporte(String cuando, CategoriaDeReporte categoria, String comentario,
            String quien) {
        return new ReporteContado(Instant.parse(cuando), categoria, comentario, quien);
    }

    @Test
    @DisplayName("cuenta en total, por dia UTC, por categoria y cuantos comentarios y personas distintos")
    void cuenta() {
        Instant desde = Instant.parse("2026-10-01T00:00:00Z");
        Instant hasta = Instant.parse("2026-10-15T00:00:00Z");
        when(repositorio.contablesEntre(desde, hasta)).thenReturn(List.of(
                reporte("2026-10-10T23:59:59Z", CategoriaDeReporte.SPAM, "c-1", "u-1"),
                reporte("2026-10-10T08:00:00Z", CategoriaDeReporte.ACOSO, "c-1", "u-2"),
                reporte("2026-10-11T00:00:00Z", CategoriaDeReporte.SPAM, "c-2", "u-1"),
                reporte("2026-10-03T12:00:00Z", CategoriaDeReporte.CONTENIDO_OFENSIVO, "c-3", "u-3")));

        FrecuenciaDeReportes.Frecuencia f = frecuencia.consultar(desde, hasta);

        assertThat(f.desde()).isEqualTo(desde);
        assertThat(f.hasta()).isEqualTo(hasta);
        assertThat(f.total()).isEqualTo(4);
        // 23:59:59 UTC del 10 es el 10; las 00:00 del 11 ya son el 11. Del mas antiguo al mas reciente.
        assertThat(f.porDia()).containsExactly(
                new FrecuenciaDeReportes.PorDia(LocalDate.parse("2026-10-03"), 1),
                new FrecuenciaDeReportes.PorDia(LocalDate.parse("2026-10-10"), 2),
                new FrecuenciaDeReportes.PorDia(LocalDate.parse("2026-10-11"), 1));
        assertThat(f.porCategoria()).containsExactly(
                java.util.Map.entry(CategoriaDeReporte.SPAM, 2L),
                java.util.Map.entry(CategoriaDeReporte.CONTENIDO_OFENSIVO, 1L),
                java.util.Map.entry(CategoriaDeReporte.ACOSO, 1L),
                java.util.Map.entry(CategoriaDeReporte.INFORMACION_FALSA, 0L),
                java.util.Map.entry(CategoriaDeReporte.CONTENIDO_INAPROPIADO, 0L),
                java.util.Map.entry(CategoriaDeReporte.VIOLACION_DE_DERECHOS, 0L));
        assertThat(f.comentariosReportados()).isEqualTo(3);
        assertThat(f.reportantes()).isEqualTo(3);
    }

    @Test
    @DisplayName("sin periodo, los ultimos 30 dias hasta ahora; un periodo sin reportes es todo cero")
    void porOmision() {
        when(repositorio.contablesEntre(any(), any())).thenReturn(List.of());

        FrecuenciaDeReportes.Frecuencia f = frecuencia.consultar(null, null);

        verify(repositorio).contablesEntre(AHORA.minus(java.time.Duration.ofDays(30)), AHORA);
        assertThat(f.total()).isZero();
        assertThat(f.porDia()).isEmpty();
        assertThat(f.porCategoria()).hasSize(6).allSatisfy((categoria, cuantos) -> assertThat(cuantos).isZero());
        assertThat(f.comentariosReportados()).isZero();
        assertThat(f.reportantes()).isZero();
    }

    @Test
    @DisplayName("solo con el fin, el inicio son 30 dias antes de ese fin")
    void soloElFin() {
        Instant hasta = Instant.parse("2026-09-30T00:00:00Z");
        when(repositorio.contablesEntre(any(), any())).thenReturn(List.of());

        frecuencia.consultar(null, hasta);

        verify(repositorio).contablesEntre(Instant.parse("2026-08-31T00:00:00Z"), hasta);
    }

    @Test
    @DisplayName("un periodo al reves, vacio o de mas de 366 dias se rechaza sin consultar")
    void rechazos() {
        Instant hasta = Instant.parse("2026-10-14T00:00:00Z");
        assertThatThrownBy(() -> frecuencia.consultar(hasta, hasta))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("«desde» tiene que ser anterior a «hasta»");
        assertThatThrownBy(() -> frecuencia.consultar(hasta.plusSeconds(1), hasta))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> frecuencia.consultar(hasta.minus(java.time.Duration.ofDays(367)), hasta))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Se pueden pedir como mucho 366 días de reportes a la vez.");
        verifyNoInteractions(repositorio);
    }

    @Test
    @DisplayName("366 dias justos si se pueden pedir")
    void unAnoBisiesto() {
        Instant hasta = Instant.parse("2026-10-14T00:00:00Z");
        when(repositorio.contablesEntre(any(), any())).thenReturn(List.of());

        assertThat(frecuencia.consultar(hasta.minus(java.time.Duration.ofDays(366)), hasta).total()).isZero();
    }
}
