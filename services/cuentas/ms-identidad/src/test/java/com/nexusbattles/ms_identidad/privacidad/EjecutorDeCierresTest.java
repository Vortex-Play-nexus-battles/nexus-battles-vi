package com.nexusbattles.ms_identidad.privacidad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Tarea programada del derecho al olvido (HU-PRV-005)")
class EjecutorDeCierresTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneId.of("UTC"));

    @Test
    @DisplayName("ejecuta las vencidas de la pasada y un fallo no detiene las demas")
    void ejecutaLasVencidas() {
        SolicitudDeCierreRepository solicitudes = mock(SolicitudDeCierreRepository.class);
        AnonimizadorDeCuentas anonimizador = mock(AnonimizadorDeCuentas.class);
        UUID primera = UUID.randomUUID();
        UUID rota = UUID.randomUUID();
        UUID tercera = UUID.randomUUID();
        when(solicitudes.vencidas(SolicitudDeCierre.PROGRAMADA, LocalDateTime.now(RELOJ),
                PageRequest.of(0, EjecutorDeCierres.LOTE))).thenReturn(List.of(primera, rota, tercera));
        when(anonimizador.ejecutar(primera)).thenReturn(true);
        when(anonimizador.ejecutar(rota)).thenThrow(new IllegalStateException("base caida"));
        when(anonimizador.ejecutar(tercera)).thenReturn(false);

        int anonimizadas = new EjecutorDeCierres(solicitudes, anonimizador, RELOJ).ejecutarVencidas();

        assertThat(anonimizadas).isEqualTo(1);
        verify(anonimizador).ejecutar(primera);
        verify(anonimizador).ejecutar(rota);
        verify(anonimizador).ejecutar(tercera);
    }

    @Test
    @DisplayName("sin vencidas no hace nada")
    void sinVencidas() {
        SolicitudDeCierreRepository solicitudes = mock(SolicitudDeCierreRepository.class);
        AnonimizadorDeCuentas anonimizador = mock(AnonimizadorDeCuentas.class);
        when(solicitudes.vencidas(SolicitudDeCierre.PROGRAMADA, LocalDateTime.now(RELOJ),
                PageRequest.of(0, EjecutorDeCierres.LOTE))).thenReturn(List.of());

        assertThat(new EjecutorDeCierres(solicitudes, anonimizador, RELOJ).ejecutarVencidas()).isZero();
    }
}
