package com.nexusbattles.plataforma.salaspartidas.chat.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nexusbattles.plataforma.salaspartidas.chat.FiltroDeContenido.Veredicto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Contrato de HU-ADM-002 y, sobre todo, que sin respuesta no se asume limpio. */
class ClienteListaNegraTest {

    private static final String URL = "http://localhost:8086/api/v1/lista-negra/verificar";

    private MockRestServiceServer servidor;
    private ClienteListaNegra cliente;

    @BeforeEach
    void montarServidorSimulado() {
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        cliente = new ClienteListaNegra(constructor.build(), URL);
    }

    @Test
    @DisplayName("un texto aprobado sale limpio y viaja con el nombre de campo del contrato")
    void aprobadoSaleLimpio() {
        servidor.expect(requestTo(URL))
                .andExpect(content().json("{\"texto\":\"vamos\"}"))
                .andRespond(withSuccess("{\"aprobado\":true}", MediaType.APPLICATION_JSON));

        assertEquals(Veredicto.LIMPIO, cliente.verificar("vamos"));
        servidor.verify();
    }

    @Test
    @DisplayName("un texto rechazado queda senalado")
    void rechazadoQuedaSenalado() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"aprobado\":false,\"motivo\":\"termino prohibido\"}", MediaType.APPLICATION_JSON));

        assertEquals(Veredicto.SENALADO, cliente.verificar("groseria"));
    }

    @Test
    @DisplayName("si la lista negra falla el veredicto es sin verificar, nunca limpio")
    void sinRespuestaNoSeAsumeLimpio() {
        servidor.expect(requestTo(URL)).andRespond(withServerError());

        assertEquals(Veredicto.SIN_VERIFICAR, cliente.verificar("da igual"));
    }
}
