package com.nexusbattles.ms_identidad.onboarding.service;

import com.nexusbattles.ms_identidad.onboarding.dto.OnboardingResponse;
import com.nexusbattles.ms_identidad.onboarding.model.EstadoOnboarding;
import com.nexusbattles.ms_identidad.onboarding.model.EstadoPaso;
import com.nexusbattles.ms_identidad.onboarding.model.OnboardingJugador;
import com.nexusbattles.ms_identidad.onboarding.model.OnboardingPaso;
import com.nexusbattles.ms_identidad.onboarding.model.PasoOnboarding;
import com.nexusbattles.ms_identidad.onboarding.repository.OnboardingJugadorRepository;
import com.nexusbattles.ms_identidad.onboarding.repository.OnboardingPasoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("OnboardingService: nace con la cuenta, se consulta y se reanuda (R17)")
class OnboardingServiceTest {

    private static final UUID UID = UUID.fromString("12345678-1234-4234-8234-123456789012");
    private static final Instant AHORA = Instant.parse("2026-09-24T12:00:00Z");
    private static final LocalDateTime HOY = LocalDateTime.ofInstant(AHORA, ZoneOffset.UTC);

    private OnboardingJugadorRepository jugadores;
    private OnboardingPasoRepository pasos;
    private ApplicationEventPublisher eventos;
    private LanzadorOnboarding lanzador;
    private OnboardingService servicio;

    @BeforeEach
    void preparar() {
        jugadores = mock(OnboardingJugadorRepository.class);
        pasos = mock(OnboardingPasoRepository.class);
        eventos = mock(ApplicationEventPublisher.class);
        lanzador = mock(LanzadorOnboarding.class);
        servicio = new OnboardingService(jugadores, pasos, eventos, lanzador, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static OnboardingJugador alta(EstadoOnboarding estado, LocalDateTime actualizado, LocalDateTime enProcesoHasta) {
        OnboardingJugador alta = new OnboardingJugador(UID, 1, "t", HOY.minusHours(1));
        ReflectionTestUtils.setField(alta, "estado", estado);
        ReflectionTestUtils.setField(alta, "actualizadoEn", actualizado);
        ReflectionTestUtils.setField(alta, "enProcesoHasta", enProcesoHasta);
        return alta;
    }

    @Test
    @DisplayName("iniciar: alta PENDIENTE, perfil hecho, tres pasos pendientes y el evento para despues del commit")
    void iniciar() {
        servicio.iniciar(UID, "profe", "4bf92f3577b34da6a3ce929d0e0e4736", "10.0.0.1");

        ArgumentCaptor<OnboardingJugador> alta = ArgumentCaptor.forClass(OnboardingJugador.class);
        verify(jugadores).save(alta.capture());
        assertThat(alta.getValue().getEstado()).isEqualTo(EstadoOnboarding.PENDIENTE);
        assertThat(alta.getValue().getVersionBootstrap()).isEqualTo(OnboardingService.VERSION_BOOTSTRAP);
        assertThat(alta.getValue().getTraza()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");

        ArgumentCaptor<OnboardingPaso> paso = ArgumentCaptor.forClass(OnboardingPaso.class);
        verify(pasos, times(4)).save(paso.capture());
        assertThat(paso.getAllValues()).extracting(OnboardingPaso::getPaso, OnboardingPaso::getEstado)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PasoOnboarding.PERFIL, EstadoPaso.HECHO),
                        org.assertj.core.groups.Tuple.tuple(PasoOnboarding.CREDITOS, EstadoPaso.PENDIENTE),
                        org.assertj.core.groups.Tuple.tuple(PasoOnboarding.HEROE, EstadoPaso.PENDIENTE),
                        org.assertj.core.groups.Tuple.tuple(PasoOnboarding.EQUIPO, EstadoPaso.PENDIENTE));
        verify(eventos).publishEvent(new JugadorRegistrado(UID, "profe", "10.0.0.1"));
        verifyNoInteractions(lanzador);
    }

    @Test
    @DisplayName("una cuenta sin alta (admin, anterior a R17) o sin uid: NO_APLICA y lista")
    void noAplica() {
        when(jugadores.findById(UID)).thenReturn(Optional.empty());

        assertThat(servicio.estadoDe(UID).estado()).isEqualTo(OnboardingResponse.NO_APLICA);
        assertThat(servicio.estadoDe(null).listo()).isTrue();
        assertThat(servicio.solicitarReintento(null).estado()).isEqualTo(OnboardingResponse.NO_APLICA);
        assertThat(servicio.solicitarReintento(UID).estado()).isEqualTo(OnboardingResponse.NO_APLICA);
        assertThat(servicio.listo(UID)).isTrue();
        assertThat(servicio.listo(null)).isTrue();
        verifyNoInteractions(lanzador);
    }

    @Test
    @DisplayName("estado: el alta y sus pasos, en orden")
    void estado() {
        OnboardingJugador completa = alta(EstadoOnboarding.COMPLETO, HOY, null);
        when(jugadores.findById(UID)).thenReturn(Optional.of(completa));
        when(pasos.findByUsuarioUid(UID)).thenReturn(List.of(
                new OnboardingPaso(UID, PasoOnboarding.EQUIPO, EstadoPaso.HECHO, "heroe=h1;equipados=a1", HOY),
                new OnboardingPaso(UID, PasoOnboarding.PERFIL, EstadoPaso.HECHO, "perfil", HOY)));

        OnboardingResponse respuesta = servicio.estadoDe(UID);

        assertThat(respuesta.listo()).isTrue();
        assertThat(respuesta.pasos()).extracting(OnboardingResponse.Paso::paso).containsExactly("PERFIL", "EQUIPO");
        assertThat(servicio.listo(UID)).isTrue();
    }

    @Test
    @DisplayName("reintento manual: se lanza si esta en error y paso la pausa; no si se acaba de intentar")
    void reintentoManual() {
        when(jugadores.findById(UID)).thenReturn(Optional.of(
                alta(EstadoOnboarding.ERROR_REINTENTABLE, HOY.minusSeconds(10), null)));
        servicio.solicitarReintento(UID);
        verify(lanzador).lanzar(UID);

        when(jugadores.findById(UID)).thenReturn(Optional.of(
                alta(EstadoOnboarding.ERROR_REINTENTABLE, HOY.minusSeconds(1), null)));
        servicio.solicitarReintento(UID);
        verify(lanzador, times(1)).lanzar(UID);
    }

    @Test
    @DisplayName("completa no se relanza; pendiente si; en proceso solo si el turno caduco")
    void admiteIntento() {
        when(jugadores.findById(UID)).thenReturn(Optional.of(alta(EstadoOnboarding.COMPLETO, HOY, null)));
        servicio.reanudarSiHaceFalta(UID);
        verify(lanzador, never()).lanzar(any());
        assertThat(servicio.listo(UID)).isTrue();

        when(jugadores.findById(UID)).thenReturn(Optional.of(alta(EstadoOnboarding.EN_PROCESO, HOY, HOY.plusMinutes(1))));
        servicio.reanudarSiHaceFalta(UID);
        verify(lanzador, never()).lanzar(any());
        assertThat(servicio.listo(UID)).isFalse();

        when(jugadores.findById(UID)).thenReturn(Optional.of(alta(EstadoOnboarding.EN_PROCESO, HOY, HOY.minusSeconds(1))));
        servicio.reanudarSiHaceFalta(UID);
        verify(lanzador, times(1)).lanzar(UID);

        when(jugadores.findById(UID)).thenReturn(Optional.of(alta(EstadoOnboarding.PENDIENTE, HOY, null)));
        servicio.reanudarSiHaceFalta(UID);
        verify(lanzador, times(2)).lanzar(UID);

        when(jugadores.findById(UID)).thenReturn(Optional.of(alta(EstadoOnboarding.ERROR_REINTENTABLE, null, null)));
        servicio.reanudarSiHaceFalta(UID);
        verify(lanzador, times(3)).lanzar(UID);

        when(jugadores.findById(UID)).thenReturn(Optional.of(alta(EstadoOnboarding.EN_PROCESO, HOY, null)));
        servicio.reanudarSiHaceFalta(UID);
        verify(lanzador, times(3)).lanzar(UID);
    }

    @Test
    @DisplayName("al iniciar sesion sin uid (cuenta antigua) no hay nada que reanudar")
    void reanudarSinUid() {
        servicio.reanudarSiHaceFalta(null);
        verifyNoInteractions(jugadores, lanzador);
    }
}
