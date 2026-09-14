package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.notificaciones.NotificacionOutbox;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.model.PujaAutomatica;
import com.nexusbattles.ms_subastas.pujas.model.TipoPuja;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.repository.PujaAutomaticaRepository;
import com.nexusbattles.ms_subastas.pujas.repository.PujaRepository;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * El disparador de las pujas automaticas (criterio 4: "emite ofertas hasta el
 * limite configurado, respetando el intervalo minimo, y se detiene notificando
 * al alcanzar el limite").
 *
 * Se resuelve con un sondeo programado, no con un evento: el intervalo minimo
 * es de 5 s de todas formas, asi que un sondeo corto cubre tanto "responde ya"
 * como "responde cuando se cumpla el enfriamiento", con un solo mecanismo que
 * ademas sobrevive a un reinicio. Una cola en memoria no.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmisionDePujasAutomaticasJobTest {

    private static final Instant AHORA = Instant.parse("2026-09-11T12:00:00Z");
    private static final UUID VENDEDOR = UUID.randomUUID();

    @Mock
    private SubastaRepository subastaRepository;

    @Mock
    private PujaAutomaticaRepository pujaAutomaticaRepository;

    @Mock
    private PujaRepository pujaRepository;

    @Mock
    private PujaApplicationService pujaApplicationService;

    @Mock
    private NotificacionOutbox outbox;

    private EmisionDePujasAutomaticasJob job;

    @BeforeEach
    void setUp() {
        ParametrosPuja parametros = new ParametrosPuja();
        Clock clock = Clock.fixed(AHORA, ZoneOffset.UTC);
        job = new EmisionDePujasAutomaticasJob(subastaRepository, pujaAutomaticaRepository, pujaRepository,
                pujaApplicationService, new MotorPujaAutomaticaService(clock, parametros), outbox, clock);
    }

    private Subasta subastaActiva(String ofertaVigente, UUID mejorPostorId) {
        return new Subasta(UUID.randomUUID(), UUID.randomUUID(), VENDEDOR, new BigDecimal(ofertaVigente),
                new BigDecimal("10"), null, mejorPostorId, EstadoSubasta.ACTIVA, AHORA.plusSeconds(3600), 0L);
    }

    private PujaAutomatica automatico(UUID subastaId, UUID jugadorId, String limite) {
        return new PujaAutomatica(UUID.randomUUID(), subastaId, jugadorId, new BigDecimal(limite), true);
    }

    private void hayUnaSubastaPendiente(Subasta subasta, List<PujaAutomatica> automaticas) {
        when(subastaRepository.findIdsConPujaAutomaticaPendiente(AHORA)).thenReturn(List.of(subasta.getId()));
        when(subastaRepository.findById(subasta.getId())).thenReturn(Optional.of(subasta));
        when(pujaAutomaticaRepository.findBySubastaIdAndActivaTrue(subasta.getId())).thenReturn(automaticas);
    }

    @Test
    void emiteLaSiguienteOfertaPorElJugadorConMayorLimite() {
        Subasta subasta = subastaActiva("100", UUID.randomUUID());
        PujaAutomatica modesta = automatico(subasta.getId(), UUID.randomUUID(), "150");
        PujaAutomatica agresiva = automatico(subasta.getId(), UUID.randomUUID(), "900");
        hayUnaSubastaPendiente(subasta, List.of(modesta, agresiva));

        job.emitirPujasAutomaticas();

        verify(pujaApplicationService).pujarAutomaticamente(eq(subasta.getId()), eq(agresiva.getJugadorId()),
                eq(new BigDecimal("110")), any());
    }

    @Test
    void noEmiteSiElJugadorNoHaCumplidoElIntervaloMinimo() {
        Subasta subasta = subastaActiva("100", UUID.randomUUID());
        PujaAutomatica automatica = automatico(subasta.getId(), UUID.randomUUID(), "900");
        hayUnaSubastaPendiente(subasta, List.of(automatica));

        Puja pujaReciente = new Puja(UUID.randomUUID(), subasta.getId(), automatica.getJugadorId(),
                new BigDecimal("90"), TipoPuja.AUTOMATICA, EstadoPuja.SUPERADA, AHORA.minusSeconds(2), "r");
        when(pujaRepository.findFirstByJugadorIdOrderByCreadaEnDesc(automatica.getJugadorId()))
                .thenReturn(Optional.of(pujaReciente));

        job.emitirPujasAutomaticas();

        verify(pujaApplicationService, never()).pujarAutomaticamente(any(), any(), any(), any());
    }

    @Test
    void emiteCuandoYaPasaronLosCincoSegundos() {
        Subasta subasta = subastaActiva("100", UUID.randomUUID());
        PujaAutomatica automatica = automatico(subasta.getId(), UUID.randomUUID(), "900");
        hayUnaSubastaPendiente(subasta, List.of(automatica));

        Puja pujaVieja = new Puja(UUID.randomUUID(), subasta.getId(), automatica.getJugadorId(),
                new BigDecimal("90"), TipoPuja.AUTOMATICA, EstadoPuja.SUPERADA, AHORA.minusSeconds(5), "r");
        when(pujaRepository.findFirstByJugadorIdOrderByCreadaEnDesc(automatica.getJugadorId()))
                .thenReturn(Optional.of(pujaVieja));

        job.emitirPujasAutomaticas();

        verify(pujaApplicationService).pujarAutomaticamente(eq(subasta.getId()), eq(automatica.getJugadorId()),
                eq(new BigDecimal("110")), any());
    }

    @Test
    void cuandoUnaAutomaticaAgotaSuLimiteSeDesactivaYSeNotificaASuDueno() {
        Subasta subasta = subastaActiva("145", UUID.randomUUID());
        PujaAutomatica agotada = automatico(subasta.getId(), UUID.randomUUID(), "150");
        hayUnaSubastaPendiente(subasta, List.of(agotada));

        job.emitirPujasAutomaticas();

        assertFalse(agotada.isActiva(), "145 + 10 = 155 excede su limite de 150");
        verify(pujaAutomaticaRepository).save(agotada);
        verify(outbox).avisarLimiteAutomaticoAlcanzado(subasta.getId(), agotada.getJugadorId(), new BigDecimal("150"));
        verify(pujaApplicationService, never()).pujarAutomaticamente(any(), any(), any(), any());
    }

    @Test
    void siUnaSubastaFallaSigueConLasDemas() {
        Subasta problematica = subastaActiva("100", UUID.randomUUID());
        Subasta sana = subastaActiva("100", UUID.randomUUID());
        PujaAutomatica deProblematica = automatico(problematica.getId(), UUID.randomUUID(), "900");
        PujaAutomatica deSana = automatico(sana.getId(), UUID.randomUUID(), "900");

        when(subastaRepository.findIdsConPujaAutomaticaPendiente(AHORA))
                .thenReturn(List.of(problematica.getId(), sana.getId()));
        when(subastaRepository.findById(problematica.getId())).thenReturn(Optional.of(problematica));
        when(subastaRepository.findById(sana.getId())).thenReturn(Optional.of(sana));
        when(pujaAutomaticaRepository.findBySubastaIdAndActivaTrue(problematica.getId())).thenReturn(List.of(deProblematica));
        when(pujaAutomaticaRepository.findBySubastaIdAndActivaTrue(sana.getId())).thenReturn(List.of(deSana));
        doThrow(new IllegalStateException("fallo simulado")).when(pujaApplicationService)
                .pujarAutomaticamente(eq(problematica.getId()), any(), any(), any());

        job.emitirPujasAutomaticas();

        verify(pujaApplicationService).pujarAutomaticamente(eq(sana.getId()), eq(deSana.getJugadorId()),
                eq(new BigDecimal("110")), any());
    }

    @Test
    void unaPujaRechazadaNoRompeElLote() {
        Subasta subasta = subastaActiva("100", UUID.randomUUID());
        PujaAutomatica automatica = automatico(subasta.getId(), UUID.randomUUID(), "900");
        hayUnaSubastaPendiente(subasta, List.of(automatica));
        doThrow(new PujaRechazadaException(PujaRechazadaException.Motivo.OFERTA_INSUFICIENTE, "perdio la carrera"))
                .when(pujaApplicationService).pujarAutomaticamente(any(), any(), any(), any());

        assertDoesNotThrow(() -> job.emitirPujasAutomaticas());
    }
}
