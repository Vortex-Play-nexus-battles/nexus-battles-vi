package com.nexusbattles.ms_finanzas.partidas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.AcreditarRequest;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.AcreditarResponse;
import com.nexusbattles.ms_finanzas.creditos.service.CreditoService;
import com.nexusbattles.ms_finanzas.partidas.ResultadoPartidaRequest.ParticipantePartidaRequest;

@ExtendWith(MockitoExtension.class)
class AcreditacionPartidaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-16T10:00:00Z");

    @Mock
    private PartidaProcesadaRepository partidaProcesadaRepositorio;

    @Mock
    private CreditoService creditoService;

    @Mock
    private CofreService cofreService;

    private AcreditacionPartidaService servicio;

    @BeforeEach
    void setUp() {
        Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        servicio = new AcreditacionPartidaService(
                partidaProcesadaRepositorio, creditoService, cofreService, reloj);
        // lenient(): el test de idempotencia (partidaYaProcesada) sale por
        // el early-return y no llama a acreditar ni al cofre, así que estos
        // stubs no se usan en TODOS los tests; sin lenient Mockito falla.
        org.mockito.Mockito.lenient().when(creditoService.acreditar(any())).thenReturn(
                new AcreditarResponse("TX-STUB", "ref-stub", "APLICADO", BigDecimal.ONE, BigDecimal.TEN));
        org.mockito.Mockito.lenient().when(cofreService.registrarCreditosGanados(anyString(), anyInt()))
                .thenReturn(Optional.empty());
    }

    @Test
    void unoAUno_ganadorRecibe2YParticipanteRecibe1() {
        when(partidaProcesadaRepositorio.existsById(any())).thenReturn(false);

        ResultadoPartidaResponse resp = servicio.procesarResultadoPartida(new ResultadoPartidaRequest(
                "partida-1v1", TipoPartida.UNO_A_UNO, "uid-ganador",
                List.of(
                        new ParticipantePartidaRequest("uid-ganador", false),
                        new ParticipantePartidaRequest("uid-perdedor", false))));

        assertThat(resp.acreditaciones()).hasSize(2);
        assertThat(resp.acreditaciones()).extracting("uid", "monto", "esGanador")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("uid-ganador", 2, true),
                        org.assertj.core.groups.Tuple.tuple("uid-perdedor", 1, false));
        assertThat(resp.sancionadosExcluidos()).isEmpty();
    }

    @Test
    void grupal_ganadorRecibe4YRestoRecibe1() {
        when(partidaProcesadaRepositorio.existsById(any())).thenReturn(false);

        ResultadoPartidaResponse resp = servicio.procesarResultadoPartida(new ResultadoPartidaRequest(
                "partida-grupal", TipoPartida.GRUPAL, "uid-g",
                List.of(
                        new ParticipantePartidaRequest("uid-g", false),
                        new ParticipantePartidaRequest("uid-b", false),
                        new ParticipantePartidaRequest("uid-c", false))));

        assertThat(resp.acreditaciones()).extracting("uid", "monto")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("uid-g", 4),
                        org.assertj.core.groups.Tuple.tuple("uid-b", 1),
                        org.assertj.core.groups.Tuple.tuple("uid-c", 1));
    }

    @Test
    void sancionado_seExcluyeYnoSeLLamaAcreditar() {
        when(partidaProcesadaRepositorio.existsById(any())).thenReturn(false);

        ResultadoPartidaResponse resp = servicio.procesarResultadoPartida(new ResultadoPartidaRequest(
                "partida-sanc", TipoPartida.GRUPAL, "uid-a",
                List.of(
                        new ParticipantePartidaRequest("uid-a", false),
                        new ParticipantePartidaRequest("uid-b", true),   // sancionado
                        new ParticipantePartidaRequest("uid-c", false))));

        assertThat(resp.acreditaciones()).hasSize(2);
        assertThat(resp.acreditaciones()).extracting("uid").containsExactly("uid-a", "uid-c");
        assertThat(resp.sancionadosExcluidos()).containsExactly("uid-b");
        // No debe llamar a acreditar para el sancionado (2 llamadas, no 3).
        verify(creditoService, times(2)).acreditar(any());
    }

    @Test
    void partidaYaProcesada_lanzaExcepcionYNoLLamaAcreditar() {
        when(partidaProcesadaRepositorio.existsById("partida-dup")).thenReturn(true);

        assertThatThrownBy(() -> servicio.procesarResultadoPartida(new ResultadoPartidaRequest(
                "partida-dup", TipoPartida.UNO_A_UNO, "uid-a",
                List.of(new ParticipantePartidaRequest("uid-a", false)))))
                .isInstanceOf(PartidaYaProcesadaException.class);

        verify(creditoService, never()).acreditar(any());
        verify(cofreService, never()).registrarCreditosGanados(anyString(), anyInt());
        verify(partidaProcesadaRepositorio, never()).save(any());
    }

    @Test
    void refIdDeLaAcreditacion_incluyePartidaYJugador() {
        when(partidaProcesadaRepositorio.existsById(any())).thenReturn(false);

        servicio.procesarResultadoPartida(new ResultadoPartidaRequest(
                "part-abc", TipoPartida.UNO_A_UNO, "uid-ganador",
                List.of(new ParticipantePartidaRequest("uid-ganador", false))));

        ArgumentCaptor<AcreditarRequest> captor = ArgumentCaptor.forClass(AcreditarRequest.class);
        verify(creditoService).acreditar(captor.capture());
        assertThat(captor.getValue().refId()).isEqualTo("partida-part-abc-jugador-uid-ganador");
    }

    @Test
    void cofreEntregado_llegaEnLaRespuesta() {
        when(partidaProcesadaRepositorio.existsById(any())).thenReturn(false);
        CofreEntregado cofre = new CofreEntregado();
        UUID cofreId = UUID.randomUUID();
        cofre.setId(cofreId);
        when(cofreService.registrarCreditosGanados(eq("uid-ganador"), anyInt()))
                .thenReturn(Optional.of(cofre));

        ResultadoPartidaResponse resp = servicio.procesarResultadoPartida(new ResultadoPartidaRequest(
                "partida-cofre", TipoPartida.UNO_A_UNO, "uid-ganador",
                List.of(new ParticipantePartidaRequest("uid-ganador", false))));

        assertThat(resp.acreditaciones()).hasSize(1);
        assertThat(resp.acreditaciones().get(0).cofreId()).isEqualTo(cofreId);
    }

    // Wrapper para eq() en el mock con anyInt de manera legible.
    private static <T> T eq(T value) { return org.mockito.ArgumentMatchers.eq(value); }

    // HU-JUE-012 CA-02 (contrato 1.2.0): en una partida por equipos gana todo el equipo.
    @Test
    void grupalPorEquipos_cadaIntegranteDelEquipoGanadorRecibe4() {
        when(partidaProcesadaRepositorio.existsById(any())).thenReturn(false);

        ResultadoPartidaResponse resp = servicio.procesarResultadoPartida(new ResultadoPartidaRequest(
                "partida-equipos", TipoPartida.GRUPAL, null, List.of("uid-a", "uid-b"),
                List.of(
                        new ParticipantePartidaRequest("uid-a", false),
                        new ParticipantePartidaRequest("uid-b", false),
                        new ParticipantePartidaRequest("uid-c", false),
                        new ParticipantePartidaRequest("uid-d", false))));

        assertThat(resp.acreditaciones()).extracting("uid", "monto", "esGanador")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("uid-a", 4, true),
                        org.assertj.core.groups.Tuple.tuple("uid-b", 4, true),
                        org.assertj.core.groups.Tuple.tuple("uid-c", 1, false),
                        org.assertj.core.groups.Tuple.tuple("uid-d", 1, false));
    }

    @Test
    void sinGanadores_todosRecibenSoloLoDeParticipar() {
        when(partidaProcesadaRepositorio.existsById(any())).thenReturn(false);

        ResultadoPartidaResponse resp = servicio.procesarResultadoPartida(new ResultadoPartidaRequest(
                "partida-empate", TipoPartida.UNO_A_UNO, null, List.of(),
                List.of(
                        new ParticipantePartidaRequest("uid-a", false),
                        new ParticipantePartidaRequest("uid-b", false))));

        assertThat(resp.acreditaciones()).extracting("monto").containsExactly(1, 1);
    }

    @Test
    void informeInvalido_es400YNoTocaElLibro() {
        org.junit.jupiter.api.Assertions.assertAll(
                () -> org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                        () -> servicio.procesarResultadoPartida(new ResultadoPartidaRequest(
                                " ", TipoPartida.UNO_A_UNO, null, List.of(new ParticipantePartidaRequest("u", false))))),
                () -> org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                        () -> servicio.procesarResultadoPartida(new ResultadoPartidaRequest(
                                "p", null, null, List.of(new ParticipantePartidaRequest("u", false))))),
                () -> org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                        () -> servicio.procesarResultadoPartida(new ResultadoPartidaRequest(
                                "p", TipoPartida.UNO_A_UNO, null, List.of()))),
                () -> org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                        () -> servicio.procesarResultadoPartida(new ResultadoPartidaRequest(
                                "p", TipoPartida.UNO_A_UNO, "otro", List.of(new ParticipantePartidaRequest("u", false))))));
        org.mockito.Mockito.verify(creditoService, org.mockito.Mockito.never()).acreditar(any());
    }
}
