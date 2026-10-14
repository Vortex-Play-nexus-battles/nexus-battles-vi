package com.nexusbattles.plataforma.adminparametros.politicas;

import com.nexusbattles.plataforma.adminparametros.parametros.ClienteAuditoria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("SA-11 · los cambios en las politicas de uso llegan a la bitacora central (§7.3.5)")
class EnvioDeAuditoriaDePoliticasTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");
    private static final OffsetDateTime EN_UTC = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
    private static final UUID ADMIN = UUID.fromString("5b2e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");

    private final CambioDePoliticaRepository cambios = mock(CambioDePoliticaRepository.class);
    private final ClienteAuditoria cliente = mock(ClienteAuditoria.class);
    private final EnvioDeAuditoriaDePoliticas envio = new EnvioDeAuditoriaDePoliticas(cambios, cliente,
            Clock.fixed(AHORA, ZoneOffset.UTC), Duration.ofSeconds(10), Duration.ofMinutes(15));

    private static CambioDePolitica publicado(String ip) {
        return new CambioDePolitica(UUID.fromString("9d0c6a52-7a59-4d4b-8f3e-2d64c1b2a001"),
                Politica.POLITICA_DE_PRIVACIDAD, "Datos del chat",
                "Los mensajes del chat se guardan noventa días y luego se borran.", "Ley de datos",
                EN_UTC.plusDays(3), ADMIN, EN_UTC.minusMinutes(1), ip);
    }

    @Test
    @DisplayName("el asiento: CREACION de politica:<POLITICA>, titulo y vigencia, motivo, IP, momento y referencia")
    void asiento() {
        ClienteAuditoria.Evento evento = EnvioDeAuditoriaDePoliticas.eventoDe(publicado("203.0.113.9"));

        assertThat(evento.tipoAccion()).isEqualTo("CREACION");
        assertThat(evento.administradorId()).isEqualTo(ADMIN.toString());
        assertThat(evento.afectado()).isEqualTo("politica:POLITICA_DE_PRIVACIDAD");
        assertThat(evento.valorAnterior()).isNull();
        assertThat(evento.valorNuevo()).isEqualTo("Datos del chat (vigente desde 2026-10-17T15:00:00Z)");
        assertThat(evento.motivo()).isEqualTo("Ley de datos");
        assertThat(evento.ipOrigen()).isEqualTo("203.0.113.9");
        assertThat(evento.ocurridoEn()).isEqualTo("2026-10-14T14:59:00Z");
        assertThat(evento.referencia()).isEqualTo("admin-parametros:politica-9d0c6a52-7a59-4d4b-8f3e-2d64c1b2a001");

        assertThat(EnvioDeAuditoriaDePoliticas.eventoDe(publicado(null)).ipOrigen()).isEqualTo("desconocida");
        assertThat(EnvioDeAuditoriaDePoliticas.eventoDe(publicado(" ")).ipOrigen()).isEqualTo("desconocida");
    }

    @Test
    @DisplayName("registrado queda auditado; rechazado se queda con su motivo y una espera")
    void entregaYRechazo() {
        CambioDePolitica bueno = publicado("203.0.113.9");
        CambioDePolitica malo = publicado("203.0.113.10");
        when(cambios.porAuditar(any(), any(Pageable.class))).thenReturn(List.of(bueno, malo));
        ArgumentCaptor<ClienteAuditoria.Evento> eventos = ArgumentCaptor.forClass(ClienteAuditoria.Evento.class);
        when(cliente.enviar(any())).thenReturn(ClienteAuditoria.Resultado.REGISTRADO,
                ClienteAuditoria.Resultado.RECHAZADO);

        assertThat(envio.ejecutar()).isEqualTo(1);

        verify(cliente, times(2)).enviar(eventos.capture());
        assertThat(eventos.getAllValues()).extracting(ClienteAuditoria.Evento::ipOrigen)
                .containsExactly("203.0.113.9", "203.0.113.10");
        assertThat(bueno.auditadoEn()).isEqualTo(EN_UTC);
        assertThat(malo.auditadoEn()).isNull();
        assertThat(malo.errorDeAuditoria()).contains("rechazado");
        verify(cambios).save(bueno);
        verify(cambios).save(malo);
    }

    @Test
    @DisplayName("si la bitacora no responde se para la vuelta: el siguiente ni se intenta")
    void caida() {
        CambioDePolitica primero = publicado("203.0.113.9");
        CambioDePolitica segundo = publicado("203.0.113.9");
        when(cambios.porAuditar(any(), any(Pageable.class))).thenReturn(List.of(primero, segundo));
        when(cliente.enviar(any())).thenThrow(new ResourceAccessException("timeout", new IOException()));

        assertThat(envio.ejecutar()).isZero();

        assertThat(primero.intentosDeAuditoria()).isEqualTo(1);
        assertThat(primero.errorDeAuditoria()).startsWith("ResourceAccessException");
        assertThat(segundo.intentosDeAuditoria()).isZero();
        verify(cliente, times(1)).enviar(any());
        verify(cambios, never()).save(segundo);

        for (int i = 0; i < 12; i++) {
            primero.falloDeAuditoria("x".repeat(400), EN_UTC, Duration.ofSeconds(10), Duration.ofMinutes(15));
        }
        assertThat(primero.errorDeAuditoria()).hasSize(300);
    }

    @Test
    @DisplayName("sin AUDITORIA_URL no hay cliente: los cambios esperan y no se consulta nada")
    void sinCliente() {
        EnvioDeAuditoriaDePoliticas apagado = new EnvioDeAuditoriaDePoliticas(cambios, null,
                Clock.fixed(AHORA, ZoneOffset.UTC), Duration.ofSeconds(10), Duration.ofMinutes(15));

        apagado.entregarPendientes();

        assertThat(apagado.ejecutar()).isZero();
        verifyNoInteractions(cambios);
    }

    @Test
    @DisplayName("la configuracion arma el envio con la misma URL de auditoria; sin ella, apagado")
    void configuracion() {
        org.springframework.beans.factory.ObjectProvider<
                com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio> sinCredencial =
                new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(
                        com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio.class);
        ConfiguracionDePoliticas configuracion = new ConfiguracionDePoliticas();

        EnvioDeAuditoriaDePoliticas apagado = configuracion.envioDeAuditoriaDePoliticas(cambios,
                Clock.fixed(AHORA, ZoneOffset.UTC), " ", 2_000, 5_000, 10_000, 900_000, sinCredencial);
        assertThat(apagado.ejecutar()).isZero();
        verifyNoInteractions(cambios);

        EnvioDeAuditoriaDePoliticas encendido = configuracion.envioDeAuditoriaDePoliticas(cambios,
                Clock.fixed(AHORA, ZoneOffset.UTC), "http://127.0.0.1:9/api/v1/admin/auditoria/eventos", 2_000,
                5_000, 10_000, 900_000, sinCredencial);
        when(cambios.porAuditar(any(), any(Pageable.class))).thenReturn(List.of());
        assertThat(encendido.ejecutar()).isZero();
        verify(cambios).porAuditar(any(), any(Pageable.class));
    }
}
