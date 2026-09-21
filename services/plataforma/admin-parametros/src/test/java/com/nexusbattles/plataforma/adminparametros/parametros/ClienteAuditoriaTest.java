package com.nexusbattles.plataforma.adminparametros.parametros;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Auditoria de cambios (HU-AUD-001)")
class ClienteAuditoriaTest {

    @Test
    @DisplayName("manda el evento con la forma de ms-cumplimiento; si la auditoria cae, el cambio no se pierde (fail-open)")
    void evento() {
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        ClienteAuditoria cliente = new ClienteAuditoria(constructor.build(), "http://cumplimiento/api/v1/admin/auditoria/eventos");
        UUID admin = UUID.randomUUID();
        Version v = new Version(1L, "chat.historial.tamano", 2, "50", "100", "Mas historial", admin,
                OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC), OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC));
        servidor.expect(requestTo("http://cumplimiento/api/v1/admin/auditoria/eventos"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"tipoAccion\":\"ACTUALIZACION\",\"administradorId\":\"" + admin
                        + "\",\"afectado\":\"parametro:chat.historial.tamano\",\"valorAnterior\":\"50\",\"valorNuevo\":\"100\",\"motivo\":\"Mas historial\"}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://cumplimiento/api/v1/admin/auditoria/eventos"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        cliente.registrar(v);
        assertThatCode(() -> cliente.registrar(v)).doesNotThrowAnyException();
        servidor.verify();
        new AuditoriaEnBitacora().registrar(v);
    }
}
