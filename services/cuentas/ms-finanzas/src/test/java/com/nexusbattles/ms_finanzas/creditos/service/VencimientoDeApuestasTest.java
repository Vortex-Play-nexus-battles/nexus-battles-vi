package com.nexusbattles.ms_finanzas.creditos.service;

import com.nexusbattles.ms_finanzas.creditos.domain.ReservaCredito;
import com.nexusbattles.ms_finanzas.creditos.repository.ReservaCreditoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Auditoria de DEV del 30-sep: 500 creditos apartados en una apuesta del 28 de
 * septiembre, sin sala ni forma de liberarlos. La reserva tenia vencimiento
 * (72 h) y nadie lo leia.
 */
@DisplayName("VencimientoDeApuestas · ninguna apuesta queda reservada para siempre")
class VencimientoDeApuestasTest {

    private static final Instant AHORA = Instant.parse("2026-10-02T23:00:00Z");

    private final ReservaCreditoRepository reservas = mock(ReservaCreditoRepository.class);
    private final CreditoService creditos = mock(CreditoService.class);
    private final VencimientoDeApuestas vencimiento =
            new VencimientoDeApuestas(reservas, creditos, Clock.fixed(AHORA, ZoneOffset.UTC), true);

    private static ReservaCredito reserva(UUID id, String jugador) {
        return ReservaCredito.builder()
                .id(id)
                .jugadorUid(jugador)
                .monto(new BigDecimal("500.00"))
                .concepto(VencimientoDeApuestas.CONCEPTO_APUESTA)
                .referenciaId("sala-" + UUID.randomUUID())
                .idempotencyKey("clave-" + id)
                .estado(ReservaCredito.EstadoReserva.ACTIVA)
                .tipoOperacion(ReservaCredito.TipoOperacion.RESERVA)
                .expiraEn(OffsetDateTime.parse("2026-10-01T18:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("libera las apuestas ACTIVAS ya vencidas, con la misma operacion idempotente de salas-partidas")
    void liberaLasVencidas() {
        UUID una = UUID.randomUUID();
        UUID otra = UUID.randomUUID();
        when(reservas.findByEstadoAndTipoOperacionAndConceptoAndExpiraEnBefore(any(), any(), any(), any(), any()))
                .thenReturn(List.of(reserva(una, "jugador-a"), reserva(otra, "jugador-b")));

        assertEquals(2, vencimiento.liberarVencidas());

        verify(creditos).liberar(una);
        verify(creditos).liberar(otra);
    }

    @Test
    @DisplayName("solo pide apuestas de sala ACTIVAS de tipo RESERVA vencidas a la hora del reloj, por lotes")
    void loQuePide() {
        when(reservas.findByEstadoAndTipoOperacionAndConceptoAndExpiraEnBefore(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        vencimiento.liberarVencidas();

        ArgumentCaptor<OffsetDateTime> antesDe = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<Pageable> lote = ArgumentCaptor.forClass(Pageable.class);
        verify(reservas).findByEstadoAndTipoOperacionAndConceptoAndExpiraEnBefore(
                eq(ReservaCredito.EstadoReserva.ACTIVA), eq(ReservaCredito.TipoOperacion.RESERVA),
                eq("apuesta-sala"), antesDe.capture(), lote.capture());
        assertEquals(AHORA, antesDe.getValue().toInstant());
        assertEquals(VencimientoDeApuestas.LOTE, lote.getValue().getPageSize());
    }

    @Test
    @DisplayName("una que falla no detiene el lote: se reintenta en la proxima vuelta")
    void unaQueFallaNoDetieneElLote() {
        UUID falla = UUID.randomUUID();
        UUID pasa = UUID.randomUUID();
        when(reservas.findByEstadoAndTipoOperacionAndConceptoAndExpiraEnBefore(any(), any(), any(), any(), any()))
                .thenReturn(List.of(reserva(falla, "jugador-a"), reserva(pasa, "jugador-b")));
        doThrow(new IllegalStateException("base ocupada")).when(creditos).liberar(falla);

        assertEquals(1, vencimiento.liberarVencidas());
        verify(creditos).liberar(pasa);
    }

    @Test
    @DisplayName("apagada por configuracion, la tarea programada no toca nada")
    void apagada() {
        VencimientoDeApuestas apagada =
                new VencimientoDeApuestas(reservas, creditos, Clock.fixed(AHORA, ZoneOffset.UTC), false);

        apagada.programado();

        verify(creditos, never()).liberar(any());
    }
}
