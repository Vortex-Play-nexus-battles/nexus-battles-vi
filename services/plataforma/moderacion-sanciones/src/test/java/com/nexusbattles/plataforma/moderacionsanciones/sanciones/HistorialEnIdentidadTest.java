package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El historial de sanciones en identidad — SA-3, canal HISTORIAL y reconciliacion. */
class HistorialEnIdentidadTest {

    private static final UUID JUGADOR = UUID.fromString("f4f4f4f4-1111-4222-8333-444444444444");
    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-10-13T12:00:00Z");

    private final ClienteIdentidad identidad = mock(ClienteIdentidad.class);
    private final SancionRepository sanciones = mock(SancionRepository.class);
    private final HistorialEnIdentidad destino = new HistorialEnIdentidad(identidad, sanciones);

    private static Sancion sancion(OffsetDateTime cuando) {
        return new Sancion(UUID.randomUUID(), JUGADOR, Sancion.Tipo.ADVERTENCIA, "Spam", null, null,
                UUID.randomUUID(), "MODERADOR", cuando, null);
    }

    private static SalidaPendiente salida() {
        return SalidaPendiente.de(CanalDeSalida.HISTORIAL, EventoDeSancion.EMISION, JUGADOR, UUID.randomUUID(),
                null, "sancion-x-emision-historial", AHORA);
    }

    @Test
    @DisplayName("manda el total de ahora y la emision mas reciente")
    void entregaElResumenDeAhora() {
        when(sanciones.countByUsuarioId(JUGADOR)).thenReturn(3L);
        when(sanciones.findFirstByUsuarioIdOrderByEmitidaEnDesc(JUGADOR)).thenReturn(Optional.of(sancion(AHORA)));

        assertThat(destino.canal()).isEqualTo(CanalDeSalida.HISTORIAL);
        assertThat(destino.entregar(salida())).isEqualTo(DestinoDeSalidas.Resultado.ENTREGADO);
        verify(identidad).historial(JUGADOR, new ClienteIdentidad.HistorialDeSanciones(3, AHORA));
    }

    @Test
    @DisplayName("sin sanciones manda cero y sin fecha, sin buscar la ultima")
    void sinSanciones() {
        when(sanciones.countByUsuarioId(JUGADOR)).thenReturn(0L);

        assertThat(destino.resumenDe(JUGADOR)).isEqualTo(new ClienteIdentidad.HistorialDeSanciones(0, null));
        verify(sanciones, never()).findFirstByUsuarioIdOrderByEmitidaEnDesc(any());
    }

    @Test
    @DisplayName("un 404 de identidad es rechazo; un destino caido sale como excepcion para reintentar")
    void rechazoYReintento() {
        when(sanciones.countByUsuarioId(JUGADOR)).thenReturn(0L);
        doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "no", null, null, null))
                .when(identidad).historial(eq(JUGADOR), any());

        assertThat(destino.entregar(salida())).isEqualTo(DestinoDeSalidas.Resultado.RECHAZADO);

        doThrow(new ResourceAccessException("caido")).when(identidad).historial(eq(JUGADOR), any());
        assertThatThrownBy(() -> destino.entregar(salida())).isInstanceOf(ResourceAccessException.class);
    }

    @Test
    @DisplayName("la reconciliacion sube el historial de cada cuenta sancionada y para si identidad no responde")
    void reconciliacion() {
        UUID otra = UUID.randomUUID();
        UUID tercera = UUID.randomUUID();
        when(sanciones.usuariosConSanciones()).thenReturn(List.of(JUGADOR, otra, tercera));
        when(sanciones.countByUsuarioId(any())).thenReturn(1L);
        when(sanciones.findFirstByUsuarioIdOrderByEmitidaEnDesc(any())).thenReturn(Optional.of(sancion(AHORA)));
        ReconciliacionDelHistorial reconciliacion = new ReconciliacionDelHistorial(destino, identidad, sanciones);

        assertThat(reconciliacion.reconciliar()).isEqualTo(3);

        doThrow(new ResourceAccessException("caido")).when(identidad).historial(eq(otra), any());
        assertThat(reconciliacion.reconciliar()).as("para en la cuenta que fallo").isEqualTo(1);
        verify(identidad, times(1)).historial(eq(tercera), any());
    }

    @Test
    @DisplayName("la vuelta programada reconcilia; sin cuentas sancionadas no llama a nadie")
    void programadaSinCuentas() {
        when(sanciones.usuariosConSanciones()).thenReturn(List.of());

        new ReconciliacionDelHistorial(destino, identidad, sanciones).programada();

        verify(identidad, never()).historial(any(), any());
    }
}
