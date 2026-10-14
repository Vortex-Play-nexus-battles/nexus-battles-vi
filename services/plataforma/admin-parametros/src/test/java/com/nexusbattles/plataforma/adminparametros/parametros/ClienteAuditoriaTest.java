package com.nexusbattles.plataforma.adminparametros.parametros;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Auditoria de cambios (HU-AUD-001, SA-5)")
class ClienteAuditoriaTest {

    private static final String URL = "http://cumplimiento/api/v1/admin/auditoria/eventos";
    private static final OffsetDateTime CAMBIO = OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID ADMIN = UUID.fromString("5b2e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");

    private static Version version(OffsetDateTime vigenteDesde, String ip) {
        Version v = new Version(7L, "chat.historial.tamano", 2, "50", "100", "Mas historial", ADMIN, CAMBIO,
                vigenteDesde);
        v.registrarOrigen(ip);
        return v;
    }

    @Test
    @DisplayName("manda el evento 1.3.0: IP de quien cambio, el momento del cambio y la referencia de la version")
    void evento() {
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        ClienteAuditoria cliente = new ClienteAuditoria(constructor.build(), URL);
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"tipoAccion\":\"ACTUALIZACION\",\"administradorId\":\"" + ADMIN
                        + "\",\"afectado\":\"parametro:chat.historial.tamano\",\"valorAnterior\":\"50\","
                        + "\"valorNuevo\":\"100\",\"motivo\":\"Mas historial\",\"ipOrigen\":\"203.0.113.9\","
                        + "\"ocurridoEn\":\"2026-10-01T10:00:00Z\",\"referencia\":\"admin-parametros:version-7\"}"))
                .andRespond(withStatus(HttpStatus.CREATED));

        assertThat(cliente.entregar(version(CAMBIO, "203.0.113.9"))).isEqualTo(ClienteAuditoria.Resultado.REGISTRADO);
        servidor.verify();
    }

    @Test
    @DisplayName("un cambio programado lo dice en el valor nuevo; sin IP, «desconocida»; sin valor, «sin definir»")
    void programadoYSinIp() {
        ClienteAuditoria.Evento evento = ClienteAuditoria.eventoDe(version(CAMBIO.plusDays(3), null));

        assertThat(evento.valorNuevo()).isEqualTo("100 (desde 2026-10-04T10:00:00Z)");
        assertThat(evento.ipOrigen()).isEqualTo("desconocida");

        Version sinValor = new Version(8L, "metricas.umbral-reportes-por-dia", 1, null, "5", "Lo fija el PO",
                ADMIN, CAMBIO, CAMBIO);
        assertThat(ClienteAuditoria.eventoDe(sinValor).valorAnterior()).isEqualTo("sin definir");
    }

    @Test
    @DisplayName("200 (ya estaba) es registrado; 400 es rechazado; 503 lanza para reintentar")
    void respuestas() {
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        ClienteAuditoria cliente = new ClienteAuditoria(constructor.build(), URL);
        servidor.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(cliente.entregar(version(CAMBIO, "203.0.113.9"))).isEqualTo(ClienteAuditoria.Resultado.REGISTRADO);
        assertThat(cliente.entregar(version(CAMBIO, "203.0.113.9"))).isEqualTo(ClienteAuditoria.Resultado.RECHAZADO);
        assertThatThrownBy(() -> cliente.entregar(version(CAMBIO, "203.0.113.9")))
                .isInstanceOf(HttpServerErrorException.class);
        new AuditoriaEnBitacora().registrar(version(CAMBIO, "203.0.113.9"));
    }
}
