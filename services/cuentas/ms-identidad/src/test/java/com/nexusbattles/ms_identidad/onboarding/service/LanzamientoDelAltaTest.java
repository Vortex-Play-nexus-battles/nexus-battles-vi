package com.nexusbattles.ms_identidad.onboarding.service;

import com.nexusbattles.ms_identidad.onboarding.auditoria.AuditoriaDeCuenta;
import com.nexusbattles.ms_identidad.onboarding.model.EstadoOnboarding;
import com.nexusbattles.ms_identidad.onboarding.repository.OnboardingJugadorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Quien pone en marcha el alta: el lanzador, el aviso del registro y el reintento programado. */
@DisplayName("Lanzamiento del alta (R17)")
class LanzamientoDelAltaTest {

    private static final UUID UID = UUID.fromString("99999999-8888-4777-8666-555555555555");

    @Test
    @DisplayName("los modos se leen con guion o guion bajo; uno desconocido cae a segundo plano")
    void modos() {
        assertThat(LanzadorOnboarding.modoDe("segundo-plano")).isEqualTo(LanzadorOnboarding.Modo.SEGUNDO_PLANO);
        assertThat(LanzadorOnboarding.modoDe(" SINCRONA ")).isEqualTo(LanzadorOnboarding.Modo.SINCRONA);
        assertThat(LanzadorOnboarding.modoDe("manual")).isEqualTo(LanzadorOnboarding.Modo.MANUAL);
        assertThat(LanzadorOnboarding.modoDe(null)).isEqualTo(LanzadorOnboarding.Modo.SEGUNDO_PLANO);
        assertThat(LanzadorOnboarding.modoDe("inventado")).isEqualTo(LanzadorOnboarding.Modo.SEGUNDO_PLANO);
    }

    @Test
    @DisplayName("sincrona: procesa en el mismo hilo y un fallo no se propaga a quien lanza")
    void sincrona() {
        ProcesadorOnboarding procesador = mock(ProcesadorOnboarding.class);
        LanzadorOnboarding lanzador = new LanzadorOnboarding(procesador, "sincrona");

        lanzador.lanzar(UID);
        verify(procesador).procesar(UID);

        when(procesador.procesar(UID)).thenThrow(new IllegalStateException("base de datos caida"));
        assertThatCode(() -> lanzador.lanzar(UID)).doesNotThrowAnyException();
        lanzador.cerrar();
    }

    @Test
    @DisplayName("manual: no lanza nada")
    void manual() {
        ProcesadorOnboarding procesador = mock(ProcesadorOnboarding.class);
        LanzadorOnboarding lanzador = new LanzadorOnboarding(procesador, "manual");

        lanzador.lanzar(UID);

        assertThat(lanzador.modo()).isEqualTo(LanzadorOnboarding.Modo.MANUAL);
        verifyNoInteractions(procesador);
    }

    @Test
    @DisplayName("segundo plano: quien lanza no espera; el alta se procesa en un hilo propio")
    void segundoPlano() throws Exception {
        ProcesadorOnboarding procesador = mock(ProcesadorOnboarding.class);
        CountDownLatch procesado = new CountDownLatch(1);
        String[] hilo = new String[1];
        doAnswer(invocacion -> {
            hilo[0] = Thread.currentThread().getName();
            procesado.countDown();
            return ProcesadorOnboarding.Resultado.COMPLETO;
        }).when(procesador).procesar(UID);
        LanzadorOnboarding lanzador = new LanzadorOnboarding(procesador, "segundo-plano");

        lanzador.lanzar(UID);

        assertThat(procesado.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(hilo[0]).startsWith("alta-jugador-");
        lanzador.cerrar();
    }

    @Test
    @DisplayName("al confirmarse el registro: primero la auditoria del alta, luego el bootstrap")
    void alRegistrar() {
        AuditoriaDeCuenta auditoria = mock(AuditoriaDeCuenta.class);
        LanzadorOnboarding lanzador = mock(LanzadorOnboarding.class);

        new AlRegistrarJugador(auditoria, lanzador).alConfirmarse(new JugadorRegistrado(UID, "profe", "10.0.0.2"));

        var orden = inOrder(auditoria, lanzador);
        orden.verify(auditoria).registro(UID, "profe", "10.0.0.2");
        orden.verify(lanzador).lanzar(UID);
    }

    @Test
    @DisplayName("reintento programado: pide las altas vencidas y un fallo en una no para las demas")
    void reintentoProgramado() {
        OnboardingJugadorRepository jugadores = mock(OnboardingJugadorRepository.class);
        ProcesadorOnboarding procesador = mock(ProcesadorOnboarding.class);
        Instant ahora = Instant.parse("2026-09-24T12:00:00Z");
        LocalDateTime hoy = LocalDateTime.ofInstant(ahora, ZoneOffset.UTC);
        UUID otro = UUID.fromString("99999999-8888-4777-8666-000000000000");
        when(jugadores.porReintentar(EstadoOnboarding.ERROR_REINTENTABLE, EstadoOnboarding.PENDIENTE,
                EstadoOnboarding.EN_PROCESO, hoy, hoy.minusMinutes(1), PageRequest.of(0, ReintentosDeAlta.LOTE)))
                .thenReturn(List.of(UID, otro));
        when(procesador.procesar(UID)).thenThrow(new IllegalStateException("fallo raro"));

        new ReintentosDeAlta(jugadores, procesador, Clock.fixed(ahora, ZoneOffset.UTC)).reintentar();

        verify(procesador).procesar(UID);
        verify(procesador).procesar(otro);
    }

    @Test
    @DisplayName("el constructor de produccion usa el reloj del sistema")
    void constructorDeProduccion() {
        OnboardingJugadorRepository jugadores = mock(OnboardingJugadorRepository.class);
        ProcesadorOnboarding procesador = mock(ProcesadorOnboarding.class);
        when(jugadores.porReintentar(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        new ReintentosDeAlta(jugadores, procesador).reintentar();

        verifyNoInteractions(procesador);
    }
}
