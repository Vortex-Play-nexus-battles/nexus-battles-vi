package com.nexusbattles.plataforma.salaspartidas.chat.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nexusbattles.plataforma.salaspartidas.chat.Canal;
import com.nexusbattles.plataforma.salaspartidas.chat.FiltroDeContenido.Veredicto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Contrato de HU-ADM-002 (moderacion-lista-negra.yaml 2.0.x) y, sobre todo,
 * que sin respuesta no se asume limpio.
 *
 * <p>El cliente se arma con {@code RestClient.builder()}, igual que el bean
 * {@code restClientChat}, asi que las respuestas se leen con los mismos
 * conversores que en produccion.
 */
class ClienteListaNegraTest {

    private static final String URL = "http://localhost:8086/api/v1/lista-negra/verificar";
    private static final Canal SALA = Canal.deSala(UUID.randomUUID());

    private MockRestServiceServer servidor;
    private ClienteListaNegra cliente;

    @BeforeEach
    void montarServidorSimulado() {
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        cliente = new ClienteListaNegra(constructor.build(), URL);
    }

    private void responde(String json) {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("un texto aprobado sale limpio y viaja con el nombre de campo del contrato")
    void aprobadoSaleLimpio() {
        servidor.expect(requestTo(URL))
                .andExpect(content().json("{\"texto\":\"vamos\",\"contexto\":\"CHAT_SALA\"}"))
                .andRespond(withSuccess("{\"aprobado\":true}", MediaType.APPLICATION_JSON));

        assertEquals(Veredicto.LIMPIO, cliente.verificar("vamos", SALA));
        servidor.verify();
    }

    @Test
    @DisplayName("HU-COM-007: el chat general manda su propio contexto")
    void elChatGeneralMandaSuContexto() {
        servidor.expect(requestTo(URL))
                .andExpect(content().json("{\"texto\":\"hola\",\"contexto\":\"CHAT_GENERAL\"}"))
                .andRespond(withSuccess("{\"aprobado\":true,\"accion\":\"PERMITIR\"}", MediaType.APPLICATION_JSON));

        assertEquals(Veredicto.LIMPIO, cliente.verificar("hola", Canal.general()));
        servidor.verify();
    }

    @Test
    @DisplayName("un texto rechazado queda senalado")
    void rechazadoQuedaSenalado() {
        responde("{\"aprobado\":false,\"motivo\":\"termino prohibido\"}");

        assertEquals(Veredicto.SENALADO, cliente.verificar("groseria", SALA));
    }

    @Test
    @DisplayName("HU-COM-007: la respuesta 2.0 con PERMITIR y campos que el chat no usa sale limpia")
    void respuestaCompletaPermitida() {
        responde("{\"aprobado\":true,\"accion\":\"PERMITIR\",\"coincidencias\":[]}");

        assertEquals(Veredicto.LIMPIO, cliente.verificar("vamos", SALA));
    }

    @Test
    @DisplayName("HU-COM-007: BLOQUEAR con categoria y coincidencias queda senalado")
    void respuestaCompletaBloqueada() {
        responde("{\"aprobado\":false,\"accion\":\"BLOQUEAR\",\"motivo\":\"El mensaje no esta permitido.\","
                + "\"categoria\":\"OFENSIVO\",\"coincidencias\":[\"groseria\"]}");

        assertEquals(Veredicto.SENALADO, cliente.verificar("groseria", SALA));
    }

    @Test
    @DisplayName("HU-COM-007: REVISION tambien bloquea, porque en el chat nadie revisa despues")
    void revisionBloquea() {
        responde("{\"aprobado\":false,\"accion\":\"REVISION\"}");

        assertEquals(Veredicto.SENALADO, cliente.verificar("dudoso", SALA));
    }

    @Test
    @DisplayName("una respuesta sin veredicto no se da por limpia")
    void sinVeredictoNoSeAsumeLimpio() {
        responde("{}");

        assertEquals(Veredicto.SIN_VERIFICAR, cliente.verificar("da igual", SALA));
    }

    @Test
    @DisplayName("si la lista negra falla el veredicto es sin verificar, nunca limpio")
    void sinRespuestaNoSeAsumeLimpio() {
        servidor.expect(requestTo(URL)).andRespond(withServerError());

        assertEquals(Veredicto.SIN_VERIFICAR, cliente.verificar("da igual", SALA));
    }
}
