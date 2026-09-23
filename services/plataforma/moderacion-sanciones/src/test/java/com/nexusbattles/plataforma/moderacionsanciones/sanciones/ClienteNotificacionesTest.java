package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@DisplayName("ClienteNotificaciones · POST /internal/notifications (HU-NOT-005 CA-04/CA-06)")
class ClienteNotificacionesTest {

    private static final String BASE = "http://srv-notificaciones:8085/api/v1";

    private final RestClient.Builder constructor = RestClient.builder();
    private final MockRestServiceServer modulo = MockRestServiceServer.bindTo(constructor).build();
    private final ClienteNotificaciones cliente = new ClienteNotificaciones(constructor.build(), BASE + "/");

    private static AvisoPendiente aviso() {
        return new AvisoPendiente(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                UUID.fromString("11111111-1111-1111-1111-111111111111"), "SANCION_ADVERTENCIA",
                "Has recibido una advertencia", "Motivo: x.", OffsetDateTime.of(2026, 9, 21, 10, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("manda la forma del contrato y el id del evento; 201 es entregado")
    void entrega() {
        modulo.expect(requestTo(BASE + "/internal/notifications"))
                .andExpect(jsonPath("$.usuarioId").value("11111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.id").value("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .andExpect(jsonPath("$.tipo").value("SANCION_ADVERTENCIA"))
                .andExpect(jsonPath("$.titulo").value("Has recibido una advertencia"))
                .andExpect(jsonPath("$.creadaEn").value("2026-09-21T10:00:00Z"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{}"));

        assertThat(cliente.entregar(aviso())).isEqualTo(EmisorDeAvisos.Resultado.ENTREGADO);
        modulo.verify();
    }

    @Test
    @DisplayName("409 = el modulo ya lo tenia: entregado, no se reintenta para siempre")
    void yaLoTenia() {
        modulo.expect(requestTo(BASE + "/internal/notifications")).andRespond(withStatus(HttpStatus.CONFLICT));
        assertThat(cliente.entregar(aviso())).isEqualTo(EmisorDeAvisos.Resultado.ENTREGADO);
    }

    @Test
    @DisplayName("400 es un rechazo: se deja a la vista, no se reintenta a ciegas")
    void rechazo() {
        modulo.expect(requestTo(BASE + "/internal/notifications")).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThat(cliente.entregar(aviso())).isEqualTo(EmisorDeAvisos.Resultado.RECHAZADO);
    }

    @Test
    @DisplayName("si no responde, se propaga para reintentar despues")
    void noResponde() {
        modulo.expect(requestTo(BASE + "/internal/notifications"))
                .andRespond(withException(new IOException("connection refused")));
        assertThatThrownBy(() -> cliente.entregar(aviso())).isInstanceOf(ResourceAccessException.class);
    }
}
