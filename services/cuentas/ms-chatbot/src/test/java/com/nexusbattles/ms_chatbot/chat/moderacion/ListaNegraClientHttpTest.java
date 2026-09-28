package com.nexusbattles.ms_chatbot.chat.moderacion;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// B11 — 7.4.8: el texto del usuario pasa por la lista negra (moderacion-lista-negra.yaml 2.0.0).
class ListaNegraClientHttpTest {

    private static final String URL = "http://moderacion/api/v1/lista-negra/verificar";

    private final RestClient.Builder constructor = RestClient.builder();
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
    private final RestClient http = constructor.build();

    @Test
    void verificaEnElContextoChatGeneralYDejaPasarLoAprobado() {
        servidor.expect(requestTo(URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("{\"texto\":\"hola\",\"contexto\":\"CHAT_GENERAL\"}"))
            .andRespond(withSuccess("{\"aprobado\":true,\"accion\":\"PERMITIR\"}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> new ListaNegraClientHttp(http, URL, "RECHAZAR").verificar("hola")).doesNotThrowAnyException();
        servidor.verify();
    }

    @Test
    void bloqueaLoQueCoincide() {
        servidor.expect(requestTo(URL))
            .andRespond(withSuccess("{\"aprobado\":false,\"accion\":\"BLOQUEAR\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new ListaNegraClientHttp(http, URL, "RECHAZAR").verificar("algo feo"))
            .isInstanceOf(ContenidoBloqueado.class);
    }

    @Test
    void sinRespuestaRechazaPorOmisionYPermiteSoloSiSeConfigura() {
        servidor.expect(times(2), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> new ListaNegraClientHttp(http, URL, "RECHAZAR").verificar("hola"))
            .isInstanceOf(ModeracionNoDisponible.class);
        assertThatCode(() -> new ListaNegraClientHttp(http, URL, "PERMITIR").verificar("hola")).doesNotThrowAnyException();
    }

    @Test
    void unaRespuestaVaciaCuentaComoNoDisponible() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess());

        assertThatThrownBy(() -> new ListaNegraClientHttp(http, URL, "RECHAZAR").verificar("hola"))
            .isInstanceOf(ModeracionNoDisponible.class);
    }

    // La lista negra acepta 2000 caracteres y el chat 4000: se verifica por
    // tramos solapados, para que un termino partido no se escape.
    @Test
    void unMensajeLargoSeVerificaPorTramosSolapados() {
        String largo = "a".repeat(3500);
        List<String> tramos = ListaNegraClientHttp.tramos(largo);
        assertThat(tramos).hasSize(2);
        assertThat(tramos.get(0)).hasSize(2000);
        assertThat(tramos.get(1)).hasSize(3500 - (2000 - 120));
        assertThat(ListaNegraClientHttp.tramos("corto")).containsExactly("corto");

        servidor.expect(times(2), requestTo(URL))
            .andRespond(withSuccess("{\"aprobado\":true}", MediaType.APPLICATION_JSON));
        new ListaNegraClientHttp(http, URL, "RECHAZAR").verificar(largo);
        servidor.verify();
    }

    @Test
    void unTextoVacioNoSeConsulta() {
        assertThatCode(() -> new ListaNegraClientHttp(http, URL, "RECHAZAR").verificar("  ")).doesNotThrowAnyException();
        assertThatCode(() -> new ListaNegraClientHttp(http, URL, "RECHAZAR").verificar(null)).doesNotThrowAnyException();
        servidor.verify();
    }
}
