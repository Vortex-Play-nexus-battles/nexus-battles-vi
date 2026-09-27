package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EntregadorDeSalidas · reintento de HU-NOT-005 CA-04, ahora por canal")
class EntregadorDeSalidasTest {

    private static final Instant AHORA = Instant.parse("2026-09-21T10:00:00Z");
    private static final OffsetDateTime AHORA_UTC = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);

    @Mock SalidaPendienteRepository salidas;
    @Mock DestinoDeSalidas avisos;
    @Mock DestinoDeSalidas identidad;
    @Mock DestinoDeSalidas correo;

    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);

    private EntregadorDeSalidas entregador(List<DestinoDeSalidas> destinos) {
        lenient().when(avisos.canal()).thenReturn(CanalDeSalida.AVISO);
        lenient().when(identidad.canal()).thenReturn(CanalDeSalida.PROYECCION);
        lenient().when(correo.canal()).thenReturn(CanalDeSalida.CORREO);
        return new EntregadorDeSalidas(salidas, destinos, reloj, Duration.ofSeconds(15), Duration.ofMinutes(15));
    }

    private static SalidaPendiente aviso() {
        return SalidaPendiente.aviso(UUID.randomUUID(), UUID.randomUUID(), "SANCION_ADVERTENCIA", "t", "c",
                AHORA_UTC.minusHours(1));
    }

    private static SalidaPendiente de(CanalDeSalida canal) {
        return SalidaPendiente.de(canal, EventoDeSancion.EMISION, UUID.randomUUID(), UUID.randomUUID(), null,
                "clave-" + UUID.randomUUID(), AHORA_UTC.minusHours(1));
    }

    @Test
    @DisplayName("lo entregado se marca; lo que no responde queda con intento, motivo y espera; lo rechazado queda a la vista")
    void ejecutar() {
        SalidaPendiente ok = aviso();
        SalidaPendiente caido = de(CanalDeSalida.PROYECCION);
        SalidaPendiente rechazado = de(CanalDeSalida.CORREO);
        when(salidas.porEntregar(any(), any())).thenReturn(List.of(ok, caido, rechazado));
        when(avisos.entregar(ok)).thenReturn(DestinoDeSalidas.Resultado.ENTREGADO);
        when(identidad.entregar(caido)).thenThrow(new RuntimeException("connection refused"));
        when(correo.entregar(rechazado)).thenReturn(DestinoDeSalidas.Resultado.RECHAZADO);

        int entregadas = entregador(List.of(avisos, identidad, correo)).ejecutar();

        assertThat(entregadas).isEqualTo(1);
        assertThat(ok.entregadoEn()).isEqualTo(AHORA_UTC);
        assertThat(caido.entregadoEn()).isNull();
        assertThat(caido.intentos()).isEqualTo(1);
        assertThat(caido.ultimoError()).contains("connection refused");
        assertThat(caido.proximoIntentoEn()).isEqualTo(AHORA_UTC.plusSeconds(15));
        assertThat(rechazado.entregadoEn()).isNull();
        assertThat(rechazado.ultimoError()).contains("rechazada");
        assertThat(rechazado.proximoIntentoEn()).isEqualTo(AHORA_UTC.plusSeconds(15));
        verify(salidas).save(ok);
        verify(salidas).save(caido);
        verify(salidas).save(rechazado);
    }

    @Test
    @DisplayName("si un destino no responde, el resto de su canal espera a la vuelta siguiente; los otros canales siguen")
    void unCanalCaidoNoFrenaALosDemas() {
        SalidaPendiente primera = de(CanalDeSalida.PROYECCION);
        SalidaPendiente segunda = de(CanalDeSalida.PROYECCION);
        SalidaPendiente avisoDetras = aviso();
        when(salidas.porEntregar(any(), any())).thenReturn(List.of(primera, segunda, avisoDetras));
        when(identidad.entregar(primera)).thenThrow(new IllegalStateException("timeout"));
        when(avisos.entregar(avisoDetras)).thenReturn(DestinoDeSalidas.Resultado.ENTREGADO);

        int entregadas = entregador(List.of(avisos, identidad, correo)).ejecutar();

        assertThat(entregadas).isEqualTo(1);
        verify(identidad, times(1)).entregar(any());
        assertThat(segunda.intentos()).as("no se intento: no cuenta").isZero();
        assertThat(segunda.proximoIntentoEn()).isNull();
        verify(salidas, never()).save(segunda);
        assertThat(avisoDetras.entregadoEn()).isNotNull();
    }

    @Test
    @DisplayName("un canal sin destino configurado no rompe la vuelta: la salida queda pendiente con el motivo")
    void sinDestino() {
        SalidaPendiente correoSinDestino = de(CanalDeSalida.CORREO);
        when(salidas.porEntregar(any(), any())).thenReturn(List.of(correoSinDestino));

        int entregadas = entregador(List.of(avisos)).ejecutar();

        assertThat(entregadas).isZero();
        assertThat(correoSinDestino.ultimoError()).contains("no hay destino");
        verify(salidas).save(correoSinDestino);
    }

    @Test
    @DisplayName("la tarea programada hace una vuelta")
    void programada() {
        when(salidas.porEntregar(any(), any())).thenReturn(List.of());

        entregador(List.of(avisos)).entregarPendientes();

        verify(salidas).porEntregar(any(), any());
    }
}
