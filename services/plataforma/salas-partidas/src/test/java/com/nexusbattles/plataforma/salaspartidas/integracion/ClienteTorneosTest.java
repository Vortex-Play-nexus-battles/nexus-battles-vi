package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.ArbitroDeTorneo.TorneoNoDisponible;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("ClienteTorneos · POST /torneos/{id}/encuentros/{n}/resultado (torneos.yaml 1.1.0)")
class ClienteTorneosTest {

    private static final String BASE = "http://srv-torneos:8083/api/v1";
    private static final UUID TORNEO = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PARTIDA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private MockRestServiceServer torneos;
    private ClienteTorneos cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder constructor = RestClient.builder();
        torneos = MockRestServiceServer.bindTo(constructor).build();
        cliente = new ClienteTorneos(constructor.build(), BASE + "/");
    }

    @Test
    @DisplayName("manda ganadorUid y partidaId al encuentro correcto; un 200 es aceptado")
    void informa() {
        torneos.expect(requestTo(BASE + "/torneos/" + TORNEO + "/encuentros/3/resultado"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.ganadorUid").value(ANA.toString()))
                .andExpect(jsonPath("$.partidaId").value(PARTIDA.toString()))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> cliente.informarGanador(TORNEO, 3, ANA, PARTIDA));
        torneos.verify();
    }

    @Test
    @DisplayName("un rechazo de torneos (422) sale como TorneoNoDisponible con el estado y el detalle")
    void rechazo() {
        torneos.expect(requestTo(BASE + "/torneos/" + TORNEO + "/encuentros/1/resultado"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"motivo\":\"GANADOR_NO_PARTICIPA\"}"));

        TorneoNoDisponible fallo = assertThrows(TorneoNoDisponible.class,
                () -> cliente.informarGanador(TORNEO, 1, ANA, PARTIDA));
        assertTrue(fallo.getMessage().contains("422"), fallo.getMessage());
        assertTrue(fallo.getMessage().contains("GANADOR_NO_PARTICIPA"), fallo.getMessage());
    }

    @Test
    @DisplayName("si torneos no responde, TorneoNoDisponible con el motivo")
    void noResponde() {
        torneos.expect(requestTo(BASE + "/torneos/" + TORNEO + "/encuentros/1/resultado"))
                .andRespond(withException(new IOException("connection refused")));

        TorneoNoDisponible fallo = assertThrows(TorneoNoDisponible.class,
                () -> cliente.informarGanador(TORNEO, 1, ANA, PARTIDA));
        assertTrue(fallo.getMessage().contains("no responde"), fallo.getMessage());
    }
}
