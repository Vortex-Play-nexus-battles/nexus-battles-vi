package com.nexusbattles.plataforma.adminparametros.parametros;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SA-5 · entrega de los cambios a la bitacora central (§7.3.5)")
class EnvioDeAuditoriaTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");
    private static final OffsetDateTime EN_UTC = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);

    private final VersionRepository versiones = mock(VersionRepository.class);
    private final ClienteAuditoria cliente = mock(ClienteAuditoria.class);
    private final EnvioDeAuditoria envio = new EnvioDeAuditoria(versiones, cliente, Clock.fixed(AHORA, ZoneOffset.UTC),
            Duration.ofSeconds(10), Duration.ofMinutes(15));

    private static Version pendiente(long id) {
        Version v = new Version(id, "chat.historial.tamano", 2, "50", "100", "Mas historial", UUID.randomUUID(),
                EN_UTC.minusMinutes(1), EN_UTC.minusMinutes(1));
        v.registrarOrigen("203.0.113.9");
        return v;
    }

    @Test
    @DisplayName("lo registrado queda auditado; lo rechazado se queda con su motivo y una espera")
    void entregaYRechazo() {
        Version buena = pendiente(1L);
        Version mala = pendiente(2L);
        when(versiones.porAuditar(any(), any(Pageable.class))).thenReturn(List.of(buena, mala));
        when(cliente.entregar(buena)).thenReturn(ClienteAuditoria.Resultado.REGISTRADO);
        when(cliente.entregar(mala)).thenReturn(ClienteAuditoria.Resultado.RECHAZADO);

        assertThat(envio.ejecutar()).isEqualTo(1);

        assertThat(buena.auditadoEn()).isEqualTo(EN_UTC);
        assertThat(mala.auditadoEn()).isNull();
        assertThat(mala.errorDeAuditoria()).contains("rechazado");
        assertThat(mala.proximoIntentoDeAuditoria()).isEqualTo(EN_UTC.plusSeconds(10));
    }

    @Test
    @DisplayName("si la bitacora central no responde se para la vuelta y la espera crece hasta su tope")
    void caida() {
        Version primera = pendiente(1L);
        Version segunda = pendiente(2L);
        when(versiones.porAuditar(any(), any(Pageable.class))).thenReturn(List.of(primera, segunda));
        when(cliente.entregar(primera)).thenThrow(new ResourceAccessException("timeout", new IOException()));

        assertThat(envio.ejecutar()).isZero();

        assertThat(primera.intentosDeAuditoria()).isEqualTo(1);
        assertThat(segunda.intentosDeAuditoria()).isZero();
        verify(cliente, never()).entregar(segunda);
        for (int i = 0; i < 12; i++) {
            primera.falloDeAuditoria("caido", EN_UTC, Duration.ofSeconds(10), Duration.ofMinutes(15));
        }
        assertThat(primera.proximoIntentoDeAuditoria()).isEqualTo(EN_UTC.plusMinutes(15));
    }
}
