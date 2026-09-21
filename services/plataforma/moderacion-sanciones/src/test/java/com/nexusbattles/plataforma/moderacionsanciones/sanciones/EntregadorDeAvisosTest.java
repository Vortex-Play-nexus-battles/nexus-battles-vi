package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EntregadorDeAvisos · reintento de HU-NOT-005 CA-04")
class EntregadorDeAvisosTest {

    @Mock AvisoPendienteRepository avisos;
    @Mock EmisorDeAvisos emisor;

    private final Clock reloj = Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC);

    private static AvisoPendiente pendiente() {
        return new AvisoPendiente(UUID.randomUUID(), UUID.randomUUID(), "SANCION_ADVERTENCIA", "t", "c",
                OffsetDateTime.of(2026, 9, 21, 9, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("lo entregado se marca; lo que no responde queda con su intento y motivo; lo rechazado queda a la vista")
    void ejecutar() {
        AvisoPendiente ok = pendiente();
        AvisoPendiente caido = pendiente();
        AvisoPendiente rechazado = pendiente();
        when(avisos.findByEntregadoEnIsNullOrderByCreadoEnAsc(any())).thenReturn(List.of(ok, caido, rechazado));
        when(emisor.entregar(ok)).thenReturn(EmisorDeAvisos.Resultado.ENTREGADO);
        when(emisor.entregar(caido)).thenThrow(new RuntimeException("connection refused"));
        when(emisor.entregar(rechazado)).thenReturn(EmisorDeAvisos.Resultado.RECHAZADO);

        int entregados = new EntregadorDeAvisos(avisos, emisor, reloj).ejecutar();

        assertThat(entregados).isEqualTo(1);
        assertThat(ok.entregadoEn()).isNotNull();
        assertThat(caido.entregadoEn()).isNull();
        assertThat(caido.intentos()).isEqualTo(1);
        assertThat(caido.ultimoError()).contains("connection refused");
        assertThat(rechazado.entregadoEn()).isNull();
        assertThat(rechazado.ultimoError()).contains("rechazo");
    }
}
