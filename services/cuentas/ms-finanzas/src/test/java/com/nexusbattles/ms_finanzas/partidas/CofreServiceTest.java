package com.nexusbattles.ms_finanzas.partidas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * El cofre por créditos ganados — §7.6 del documento, cofres.yaml 1.1.0 (B7).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CofreService · 20 créditos ganados, dos cofres por semana ISO, contenido sorteado")
class CofreServiceTest {

    /** Miércoles 16 de septiembre de 2026 a las 10:00 UTC — semana ISO 2026-W38 en Bogotá. */
    private static final Instant AHORA = Instant.parse("2026-09-16T10:00:00Z");
    private static final String SEMANA = "2026-W38";
    private static final TablaDeCofre TABLA = new TablaDeCofre("PRUEBA-1", List.of(
            new TablaDeCofre.Entrada("producto-a", 1), new TablaDeCofre.Entrada("producto-b", 3)));

    @Mock
    private ContadorDeCofresRepository contadores;

    @Mock
    private CofreEntregadoRepository cofres;

    private CofreService servicio;

    private CofreService servicioCon(boolean conservarSobrante, Instant ahora) {
        return new CofreService(contadores, cofres,
                new ReglasDeCofres(ZoneId.of("America/Bogota"), conservarSobrante, TABLA, 30),
                Clock.fixed(ahora, ZoneOffset.UTC), new Random(99));
    }

    @BeforeEach
    void setUp() {
        servicio = servicioCon(true, AHORA);
        org.mockito.Mockito.lenient().when(cofres.save(any(CofreEntregado.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ContadorDeCofres contadorCon(int creditos) {
        ContadorDeCofres contador = ContadorDeCofres.nuevo("uid-1");
        contador.sumar(creditos, AHORA.minusSeconds(3600));
        return contador;
    }

    private ContadorDeCofres guardado() {
        ArgumentCaptor<ContadorDeCofres> captor = ArgumentCaptor.forClass(ContadorDeCofres.class);
        verify(contadores).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("por debajo de 20 solo acumula, sin cofre")
    void acumula() {
        when(contadores.findById("uid-1")).thenReturn(Optional.empty());

        Optional<CofreEntregado> cofre = servicio.registrarCreditosGanados("uid-1", 2);

        assertThat(cofre).isEmpty();
        assertThat(guardado().getCreditosGanados()).isEqualTo(2);
        verify(cofres, never()).save(any());
    }

    @Test
    @DisplayName("cero o menos no es una victoria: no toca nada")
    void sinCreditosGanadosNoHaceNada() {
        assertThat(servicio.registrarCreditosGanados("uid-1", 0)).isEmpty();
        assertThat(servicio.registrarCreditosGanados("uid-1", -4)).isEmpty();
        verifyNoInteractions(contadores, cofres);
    }

    @Test
    @DisplayName("al llegar a 20 hay cofre, el contador se reinicia y el contenido sale de la tabla con su semilla")
    void completaLaCuota() {
        when(contadores.findById("uid-1")).thenReturn(Optional.of(contadorCon(18)));
        when(cofres.countByUidJugadorAndSemanaIso("uid-1", SEMANA)).thenReturn(0L);

        CofreEntregado cofre = servicio.registrarCreditosGanados("uid-1", 2).orElseThrow();

        long semillaEsperada = new Random(99).nextLong();
        assertThat(guardado().getCreditosGanados()).isZero();
        assertThat(cofre.getUidJugador()).isEqualTo("uid-1");
        assertThat(cofre.getSemanaIso()).isEqualTo(SEMANA);
        assertThat(cofre.getEntregadoEn()).isEqualTo(AHORA);
        assertThat(cofre.getSemilla()).isEqualTo(semillaEsperada);
        assertThat(cofre.getTablaVersion()).isEqualTo("PRUEBA-1");
        assertThat(cofre.getContenido()).isEqualTo(TABLA.sortear(semillaEsperada).productoId());
        assertThat(cofre.getPremios()).containsExactly(new PremioDeCofre(cofre.getContenido(), 1));
        assertThat(cofre.getEstadoEntrega()).isEqualTo(CofreEntregado.EstadoEntrega.PENDIENTE);
        assertThat(cofre.getId()).isNotNull();
        assertThat(cofre.isNew()).isTrue();
        assertThat(cofre.claveDeEntrega()).isEqualTo("cofre-" + cofre.getId());
    }

    @Test
    @DisplayName("D-B7-17: el sobrante (22 → 2) cuenta para el siguiente cofre; configurable a cero")
    void sobrante() {
        when(contadores.findById("uid-1")).thenReturn(Optional.of(contadorCon(18)));
        when(cofres.countByUidJugadorAndSemanaIso(anyString(), anyString())).thenReturn(0L);

        servicio.registrarCreditosGanados("uid-1", 4);

        assertThat(guardado().getCreditosGanados()).isEqualTo(2);
    }

    @Test
    @DisplayName("D-B7-17: sin conservar el sobrante, el contador vuelve a cero")
    void sinSobrante() {
        CofreService sinConservar = servicioCon(false, AHORA);
        when(contadores.findById("uid-1")).thenReturn(Optional.of(contadorCon(18)));
        when(cofres.countByUidJugadorAndSemanaIso(anyString(), anyString())).thenReturn(0L);

        assertThat(sinConservar.registrarCreditosGanados("uid-1", 4)).isPresent();

        assertThat(guardado().getCreditosGanados()).isZero();
    }

    @Test
    @DisplayName("el segundo cofre de la semana también se entrega")
    void segundoDeLaSemana() {
        when(contadores.findById("uid-1")).thenReturn(Optional.of(contadorCon(19)));
        when(cofres.countByUidJugadorAndSemanaIso("uid-1", SEMANA)).thenReturn(1L);

        assertThat(servicio.registrarCreditosGanados("uid-1", 2)).isPresent();
    }

    @Test
    @DisplayName("§7.6: con dos cofres esta semana no hay tercero, y la cuota se consume igual (D-B7-17)")
    void topeSemanal() {
        when(contadores.findById("uid-1")).thenReturn(Optional.of(contadorCon(19)));
        when(cofres.countByUidJugadorAndSemanaIso("uid-1", SEMANA)).thenReturn(2L);

        Optional<CofreEntregado> cofre = servicio.registrarCreditosGanados("uid-1", 2);

        assertThat(cofre).isEmpty();
        assertThat(guardado().getCreditosGanados()).isEqualTo(1);
        verify(cofres, never()).save(any());
    }

    @Test
    @DisplayName("lo acumulado se conserva de una semana a otra: el contador no es semanal")
    void noEsSemanal() {
        // 18 acumulados la semana anterior: el lunes siguiente, con 2 mas, cofre.
        CofreService elLunes = servicioCon(true, Instant.parse("2026-09-21T15:00:00Z"));
        when(contadores.findById("uid-1")).thenReturn(Optional.of(contadorCon(18)));
        when(cofres.countByUidJugadorAndSemanaIso("uid-1", "2026-W39")).thenReturn(0L);

        CofreEntregado cofre = elLunes.registrarCreditosGanados("uid-1", 2).orElseThrow();

        assertThat(cofre.getSemanaIso()).isEqualTo("2026-W39");
    }

    @Test
    @DisplayName("la semana se cuenta en la zona del cofre: el domingo 22:00 en Bogotá aún es la semana anterior")
    void semanaEnSuZona() {
        // Lunes 28-sep 03:00 UTC = domingo 27-sep 22:00 en Bogota (UTC-5).
        CofreService elDomingoEnBogota = servicioCon(true, Instant.parse("2026-09-28T03:00:00Z"));
        when(contadores.findById("uid-1")).thenReturn(Optional.of(contadorCon(18)));
        when(cofres.countByUidJugadorAndSemanaIso("uid-1", "2026-W39")).thenReturn(0L);

        assertThat(elDomingoEnBogota.registrarCreditosGanados("uid-1", 2).orElseThrow().getSemanaIso())
                .isEqualTo("2026-W39");
    }

    @Test
    @DisplayName("un cofre es de un jugador concreto")
    void sinJugador() {
        assertThatThrownBy(() -> servicio.registrarCreditosGanados(null, 2)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("semana ISO 8601: año de la semana, no del calendario")
    void semanaIso() {
        ReglasDeCofres enUtc = new ReglasDeCofres(ZoneOffset.UTC, true, TABLA, 30);

        assertThat(enUtc.semanaIso(Instant.parse("2026-09-16T10:00:00Z"))).isEqualTo("2026-W38");
        // 1-ene-2027 es viernes: pertenece a la ultima semana de 2026, la 53.
        assertThat(enUtc.semanaIso(Instant.parse("2027-01-01T12:00:00Z"))).isEqualTo("2026-W53");
        // 31-dic-2029 es lunes: ya es la primera semana de 2030.
        assertThat(enUtc.semanaIso(Instant.parse("2029-12-31T12:00:00Z"))).isEqualTo("2030-W01");
    }

    @Test
    @DisplayName("las reglas exigen zona, tabla y una espera de al menos un minuto")
    void reglasValidas() {
        assertThatThrownBy(() -> new ReglasDeCofres(null, true, TABLA, 30)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReglasDeCofres(ZoneOffset.UTC, true, null, 30))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReglasDeCofres(ZoneOffset.UTC, true, TABLA, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el contador no admite créditos negativos")
    void contadorSinNegativos() {
        assertThatThrownBy(() -> ContadorDeCofres.nuevo("uid-1").sumar(-1, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
