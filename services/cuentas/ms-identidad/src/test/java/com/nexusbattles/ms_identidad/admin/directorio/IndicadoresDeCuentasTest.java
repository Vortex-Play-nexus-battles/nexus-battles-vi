package com.nexusbattles.ms_identidad.admin.directorio;

import com.nexusbattles.ms_identidad.admin.dto.IndicadoresDeCuentasResponse;
import com.nexusbattles.ms_identidad.admin.dto.IndicadoresDeCuentasResponse.RegistrosDelDia;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Indicadores de cuentas del panel de administracion — HU-USR-008 (#561).
 *
 * <p>Sin base de datos: los conteos los da un doble de {@link ConteosDeCuentas}
 * (que se prueba contra H2 en {@code ConteosDeCuentasTest}). Aqui se prueba lo
 * que se calcula con ellos: el rango, la serie continua y los estados.
 */
@DisplayName("Indicadores de cuentas (HU-USR-008)")
class IndicadoresDeCuentasTest {

    /** 5 de octubre de 2026 a las 15:00 en Bogota. */
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T20:00:00Z"), ZONA);
    private static final LocalDate HOY = LocalDate.of(2026, 10, 5);

    private final ConteosDeCuentas conteos = mock(ConteosDeCuentas.class);
    private final CuentasDePrueba cuentasDePrueba = new CuentasDePrueba("nexus.test", "qa_");
    private IndicadoresDeCuentas indicadores;

    @BeforeEach
    void preparar() {
        indicadores = new IndicadoresDeCuentas(conteos, cuentasDePrueba, RELOJ, 30, 366);
        when(conteos.porEstadoGuardado(any())).thenReturn(Map.of());
        when(conteos.altasEntre(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("sin rango, los ultimos 30 dias hasta hoy, con un elemento por dia")
    void rangoPorOmision() {
        IndicadoresDeCuentasResponse respuesta = indicadores.calcular(null, null, false);

        assertThat(respuesta.registros().desde()).isEqualTo(HOY.minusDays(29));
        assertThat(respuesta.registros().hasta()).isEqualTo(HOY);
        assertThat(respuesta.registros().porDia()).hasSize(30);
        assertThat(respuesta.registros().porDia().get(0).fecha()).isEqualTo(HOY.minusDays(29));
        assertThat(respuesta.registros().porDia().get(29).fecha()).isEqualTo(HOY);
        verify(conteos).altasEntre(any(), eq(HOY.minusDays(29).atStartOfDay()), eq(HOY.plusDays(1).atStartOfDay()));
    }

    @Test
    @DisplayName("la serie es continua: los dias sin altas van con 0, no faltan")
    void serieContinua() {
        when(conteos.altasEntre(any(), any(), any())).thenReturn(List.of(
                LocalDateTime.of(2026, 10, 1, 9, 30),
                LocalDateTime.of(2026, 10, 1, 23, 59),
                LocalDateTime.of(2026, 10, 3, 0, 0)));

        IndicadoresDeCuentasResponse respuesta = indicadores.calcular("2026-10-01", "2026-10-04", false);

        assertThat(respuesta.registros().porDia()).containsExactly(
                new RegistrosDelDia(LocalDate.of(2026, 10, 1), 2),
                new RegistrosDelDia(LocalDate.of(2026, 10, 2), 0),
                new RegistrosDelDia(LocalDate.of(2026, 10, 3), 1),
                new RegistrosDelDia(LocalDate.of(2026, 10, 4), 0));
        assertThat(respuesta.registros().total()).isEqualTo(3);
        verify(conteos).altasEntre(any(),
                eq(LocalDateTime.of(2026, 10, 1, 0, 0)), eq(LocalDateTime.of(2026, 10, 5, 0, 0)));
    }

    @Test
    @DisplayName("los cinco estados salen siempre, con 0 si no hay ninguna cuenta en ellos")
    void cincoEstadosSiempre() {
        when(conteos.porEstadoGuardado(any())).thenReturn(Map.of("ACTIVO", 7L));

        IndicadoresDeCuentasResponse respuesta = indicadores.calcular(null, null, false);

        assertThat(respuesta.porEstado()).containsExactly(
                Map.entry("ACTIVO", 7L),
                Map.entry("PENDIENTE_VERIFICACION", 0L),
                Map.entry("INACTIVO", 0L),
                Map.entry("SUSPENDIDO", 0L),
                Map.entry("BANEADO", 0L));
        assertThat(respuesta.total()).isEqualTo(7);
    }

    @Test
    @DisplayName("las filas anteriores a B2 cuentan en su estado, y el total es la suma")
    void formasAnterioresSumanEnSuEstado() {
        Map<String, Long> guardados = new LinkedHashMap<>();
        guardados.put("ACTIVO", 10L);
        guardados.put("SUSPENDIDO", 2L);
        guardados.put("SUSPENDIDA", 1L);
        guardados.put("BANEADA", 4L);
        guardados.put("PENDIENTE_VERIFICACION", 3L);
        when(conteos.porEstadoGuardado(any())).thenReturn(guardados);

        IndicadoresDeCuentasResponse respuesta = indicadores.calcular(null, null, false);

        assertThat(respuesta.porEstado()).containsEntry("SUSPENDIDO", 3L).containsEntry("BANEADO", 4L)
                .doesNotContainKeys("SUSPENDIDA", "BANEADA");
        assertThat(respuesta.total()).isEqualTo(20);
    }

    @Test
    @DisplayName("un estado guardado que no es del contrato se publica con su nombre, no se esconde")
    void estadoRaroNoSeEsconde() {
        when(conteos.porEstadoGuardado(any())).thenReturn(Map.of("ACTIVO", 1L, "CONGELADO", 2L));

        IndicadoresDeCuentasResponse respuesta = indicadores.calcular(null, null, false);

        assertThat(respuesta.porEstado()).containsEntry("CONGELADO", 2L);
        assertThat(respuesta.total()).isEqualTo(3);
    }

    @Test
    @DisplayName("con ocultarPruebas, el mismo criterio que el directorio en las dos consultas")
    @SuppressWarnings("unchecked")
    void ocultarPruebasUsaElCriterioDelDirectorio() {
        IndicadoresDeCuentasResponse respuesta = indicadores.calcular(null, null, true);

        ArgumentCaptor<Specification<Usuario>> porEstado = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Specification<Usuario>> altas = ArgumentCaptor.forClass(Specification.class);
        verify(conteos).porEstadoGuardado(porEstado.capture());
        verify(conteos).altasEntre(altas.capture(), any(), any());
        assertThat(porEstado.getValue()).isNotNull();
        assertThat(altas.getValue()).isSameAs(porEstado.getValue());
        assertThat(respuesta.ocultarPruebas()).isTrue();
    }

    @Test
    @DisplayName("solo desde: hasta hoy; solo hasta: los dias por omision hacia atras")
    void rangoAMedias() {
        IndicadoresDeCuentasResponse soloDesde = indicadores.calcular("2026-09-20", null, false);
        IndicadoresDeCuentasResponse soloHasta = indicadores.calcular(null, "2026-09-30", false);

        assertThat(soloDesde.registros().desde()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(soloDesde.registros().hasta()).isEqualTo(HOY);
        assertThat(soloHasta.registros().desde()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(soloHasta.registros().hasta()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    @DisplayName("desde posterior a hasta es 400 y no consulta nada")
    void desdePosterior() {
        assertThatThrownBy(() -> indicadores.calcular("2026-10-05", "2026-10-01", false))
                .isInstanceOf(ConsultaInvalidaException.class)
                .hasMessageContaining("posterior");
        verify(conteos, never()).porEstadoGuardado(any());
    }

    @Test
    @DisplayName("un rango mas largo que el tope tecnico es 400 con las dos cifras")
    void rangoDemasiadoLargo() {
        assertThatThrownBy(() -> indicadores.calcular("2025-01-01", "2026-10-05", false))
                .isInstanceOf(ConsultaInvalidaException.class)
                .hasMessageContaining("643")
                .hasMessageContaining("366");
        verify(conteos, never()).altasEntre(any(), any(), any());
    }

    @Test
    @DisplayName("el tope es inclusive: 366 dias exactos se aceptan")
    void topeInclusive() {
        IndicadoresDeCuentasResponse respuesta = indicadores.calcular("2025-10-05", "2026-10-05", false);

        assertThat(respuesta.registros().porDia()).hasSize(366);
    }

    @Test
    @DisplayName("una fecha mal escrita es 400 y nombra el campo")
    void fechaMalEscrita() {
        assertThatThrownBy(() -> indicadores.calcular("05-10-2026", null, false))
                .isInstanceOf(ConsultaInvalidaException.class)
                .hasMessageContaining("«desde»");
    }

    @Test
    @DisplayName("dice cuando se conto, con el reloj del servicio")
    void calculadoEn() {
        assertThat(indicadores.calcular(null, null, false).calculadoEn().toInstant())
                .isEqualTo(Instant.parse("2026-10-05T20:00:00Z"));
    }

    @Test
    @DisplayName("una configuracion imposible no arranca: dias por omision fuera del tope")
    void configuracionImposible() {
        assertThatThrownBy(() -> new IndicadoresDeCuentas(conteos, cuentasDePrueba, RELOJ, 0, 366))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new IndicadoresDeCuentas(conteos, cuentasDePrueba, RELOJ, 400, 366))
                .isInstanceOf(IllegalStateException.class);
    }
}
