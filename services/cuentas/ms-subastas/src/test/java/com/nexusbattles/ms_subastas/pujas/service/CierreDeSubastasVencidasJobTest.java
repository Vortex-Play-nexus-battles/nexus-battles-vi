package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El disparador del cierre por vencimiento. La logica de restitucion de
 * creditos ya vive en MotorPujasService.cerrarPorVencimiento; esto solo decide
 * a quien hay que cerrar y cuando.
 */
@ExtendWith(MockitoExtension.class)
class CierreDeSubastasVencidasJobTest {

    private static final Instant AHORA = Instant.parse("2026-09-11T12:00:00Z");

    @Mock
    private SubastaRepository subastaRepository;

    @Mock
    private PujaApplicationService pujaApplicationService;

    private CierreDeSubastasVencidasJob job;

    @BeforeEach
    void setUp() {
        job = new CierreDeSubastasVencidasJob(subastaRepository, pujaApplicationService,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void cierraTodasLasSubastasVencidasQueSiguenActivas() {
        UUID una = UUID.randomUUID();
        UUID otra = UUID.randomUUID();
        when(subastaRepository.findIdsDeActivasVencidas(AHORA)).thenReturn(List.of(una, otra));

        job.cerrarVencidas();

        verify(pujaApplicationService).cerrarPorVencimiento(una);
        verify(pujaApplicationService).cerrarPorVencimiento(otra);
    }

    @Test
    void siUnaSubastaFallaAlCerrarseSigueConLasDemas() {
        UUID problematica = UUID.randomUUID();
        UUID sana = UUID.randomUUID();
        when(subastaRepository.findIdsDeActivasVencidas(AHORA)).thenReturn(List.of(problematica, sana));
        doThrow(new IllegalStateException("fallo simulado"))
                .when(pujaApplicationService).cerrarPorVencimiento(problematica);

        job.cerrarVencidas();

        verify(pujaApplicationService).cerrarPorVencimiento(sana);
    }

    @Test
    void siNoHayVencidasNoTocaNada() {
        when(subastaRepository.findIdsDeActivasVencidas(AHORA)).thenReturn(List.of());

        job.cerrarVencidas();

        verify(pujaApplicationService, never()).cerrarPorVencimiento(any());
    }
}
