package com.nexusbattles.ms_subastas.notificaciones;

import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 7.7.8: «Aviso 1 hora antes de finalizar subastas en las que se participa»
 * y «Recordatorio de subastas guardadas en lista de seguimiento». Una sola vez
 * por subasta, y nunca de algo que ya termino.
 */
@DisplayName("Recordatorio de 1 hora antes del cierre (7.7.8)")
class RecordatorioDeCierreJobTest {

    private static final Instant AHORA = Instant.parse("2026-09-20T12:00:00Z");

    private final SubastaRepository subastas = mock(SubastaRepository.class);
    private final AvisosDeSubasta avisos = mock(AvisosDeSubasta.class);
    private RecordatorioDeCierreJob job;

    @BeforeEach
    void preparar() {
        job = new RecordatorioDeCierreJob(subastas, avisos, Clock.fixed(AHORA, ZoneOffset.UTC),
                mock(PlatformTransactionManager.class));
    }

    private Subasta subasta(Duration faltan, EstadoSubasta estado) {
        Subasta subasta = new Subasta(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN,
                BigDecimal.ONE, null, null, estado, AHORA.plus(faltan), 0L);
        when(subastas.findByIdParaActualizar(subasta.getId())).thenReturn(Optional.of(subasta));
        return subasta;
    }

    @Test
    @DisplayName("busca las que cierran en la proxima hora y avisa una vez, dejando constancia")
    void avisaYMarca() {
        Subasta porCerrar = subasta(Duration.ofMinutes(40), EstadoSubasta.ACTIVA);
        when(subastas.idsParaRecordar(AHORA, AHORA.plus(Duration.ofHours(1)))).thenReturn(List.of(porCerrar.getId()));

        job.recordar();

        verify(avisos).recordatorio(porCerrar);
        assertEquals(AHORA, porCerrar.getRecordatorioEnviadoEn());
        verify(subastas).save(porCerrar);
    }

    @Test
    @DisplayName("una segunda pasada (u otra replica) no repite el recordatorio")
    void noRepite() {
        Subasta yaAvisada = subasta(Duration.ofMinutes(40), EstadoSubasta.ACTIVA);
        yaAvisada.setRecordatorioEnviadoEn(AHORA.minusSeconds(60));

        assertFalse(job.recordarUna(yaAvisada.getId()));
        verifyNoInteractions(avisos);
    }

    @Test
    @DisplayName("ni de una terminada, ni de una vencida, ni de una que ya no existe")
    void soloDeLasActivasPorCerrar() {
        Subasta cancelada = subasta(Duration.ofMinutes(40), EstadoSubasta.CANCELADA);
        Subasta vencida = subasta(Duration.ofSeconds(0), EstadoSubasta.ACTIVA);
        UUID inexistente = UUID.randomUUID();
        when(subastas.findByIdParaActualizar(inexistente)).thenReturn(Optional.empty());

        assertFalse(job.recordarUna(cancelada.getId()));
        assertFalse(job.recordarUna(vencida.getId()));
        assertFalse(job.recordarUna(inexistente));
        verifyNoInteractions(avisos);
    }

    @Test
    @DisplayName("una que falla no impide las demas")
    void unFalloNoFrenaLaPasada() {
        Subasta mala = subasta(Duration.ofMinutes(10), EstadoSubasta.ACTIVA);
        Subasta buena = subasta(Duration.ofMinutes(20), EstadoSubasta.ACTIVA);
        when(subastas.idsParaRecordar(any(), any())).thenReturn(List.of(mala.getId(), buena.getId()));
        doThrow(new IllegalStateException("fallo")).when(avisos).recordatorio(mala);

        job.recordar();

        verify(avisos).recordatorio(buena);
        assertEquals(AHORA, buena.getRecordatorioEnviadoEn());
    }
}
