package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.AvisoDeInvitacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@DisplayName("ClienteNotificacionesDeInvitaciones · la invitacion llega a la bandeja (1.10.0)")
class ClienteNotificacionesDeInvitacionesTest {

    private static final String BASE = "http://localhost:8085/api/v1/";
    private static final String DESTINO = "http://localhost:8085/api/v1/internal/notifications";
    private static final UUID SALA = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID INVITADO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant AHORA = Instant.parse("2026-10-06T18:00:00Z");

    private RestClient.Builder constructor;
    private MockRestServiceServer servidor;

    @BeforeEach
    void montar() {
        constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
    }

    private ClienteNotificacionesDeInvitaciones cliente(String base) {
        return new ClienteNotificacionesDeInvitaciones(constructor.build(), base);
    }

    private static AvisoDeInvitacion.Invitacion publica() {
        return new AvisoDeInvitacion.Invitacion(SALA, INVITADO, "Simon_P", "UNO_CONTRA_UNO", false, null, 50, AHORA);
    }

    private static AvisoDeInvitacion.Invitacion privada() {
        return new AvisoDeInvitacion.Invitacion(SALA, INVITADO, "Simon_P", "HASTA_SEIS", true, "K7Q2-M9XA", 0, AHORA);
    }

    @Test
    @DisplayName("sala publica: al invitado, con id estable por sala e invitado, sin codigo")
    void salaPublica() {
        servidor.expect(requestTo(DESTINO))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.usuarioId").value(INVITADO.toString()))
                .andExpect(jsonPath("$.id").value("sala:" + SALA + ":invitacion:" + INVITADO))
                .andExpect(jsonPath("$.tipo").value("INVITACION_SALA"))
                .andExpect(jsonPath("$.titulo").value("Simon_P te invita a una batalla"))
                .andExpect(jsonPath("$.cuerpo").value("Sala 1 contra 1 · 50 créditos en juego."))
                .andExpect(jsonPath("$.creadaEn").value("2026-10-06T18:00:00Z"))
                .andRespond(withStatus(HttpStatus.CREATED));

        assertTrue(cliente(BASE).invitar(publica()));
        servidor.verify();
    }

    @Test
    @DisplayName("sala privada: el codigo viaja en el id y en el texto, porque es lo que deja entrar")
    void salaPrivada() {
        servidor.expect(requestTo(DESTINO))
                .andExpect(jsonPath("$.id").value("sala:" + SALA + ":invitacion:" + INVITADO + ":codigo:K7Q2-M9XA"))
                .andExpect(jsonPath("$.cuerpo")
                        .value("Sala Hasta seis · privada. Código de invitación: K7Q2-M9XA."))
                .andRespond(withStatus(HttpStatus.CREATED));

        assertTrue(cliente(BASE).invitar(privada()));
        servidor.verify();
    }

    @Test
    @DisplayName("el id cabe en el limite del contrato de notificaciones (200)")
    void idDentroDelLimite() {
        assertTrue(ClienteNotificacionesDeInvitaciones.idDelAviso(privada()).length() <= 200);
        assertEquals("INVITACION_SALA", ClienteNotificacionesDeInvitaciones.TIPO);
        assertTrue(ClienteNotificacionesDeInvitaciones.TIPO.length() <= 40);
    }

    @Test
    @DisplayName("un 409 es la misma invitacion otra vez: no salio un segundo aviso, y no es un error")
    void yaEstaba() {
        servidor.expect(requestTo(DESTINO)).andRespond(withStatus(HttpStatus.CONFLICT));

        assertFalse(cliente(BASE).invitar(publica()));
        servidor.verify();
    }

    @Test
    @DisplayName("si notificaciones falla, la invitacion no salio y se dice")
    void caido() {
        servidor.expect(requestTo(DESTINO)).andRespond(withServerError());

        assertThrows(AvisoDeInvitacion.AvisoNoDisponible.class, () -> cliente(BASE).invitar(publica()));
        servidor.verify();
    }

    @Test
    @DisplayName("sin URL de notificaciones no se finge el envio")
    void sinUrl() {
        servidor.expect(never(), requestTo(DESTINO));

        assertThrows(AvisoDeInvitacion.AvisoNoDisponible.class, () -> cliente(" ").invitar(publica()));
        assertThrows(AvisoDeInvitacion.AvisoNoDisponible.class, () -> cliente(null).invitar(publica()));
        servidor.verify();
    }
}
