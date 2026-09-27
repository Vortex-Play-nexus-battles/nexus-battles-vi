package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.integracion;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.FiltroDeMensajesPrivados.Veredicto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("ClienteListaNegraMensajesPrivados · contexto MENSAJE_PRIVADO, fallo cerrado (B6)")
class ClienteListaNegraMensajesPrivadosTest {

    private static final String URL = "http://localhost:8086/api/v1/lista-negra/verificar";

    private MockRestServiceServer servidor;
    private ClienteListaNegraMensajesPrivados filtro;

    @BeforeEach
    void montar() {
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        filtro = new ClienteListaNegraMensajesPrivados(constructor.build(), URL);
    }

    private void responde(String json) {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("manda el texto con contexto MENSAJE_PRIVADO y PERMITIR entrega")
    void permitir() {
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.texto").value("hola"))
                .andExpect(jsonPath("$.contexto").value("MENSAJE_PRIVADO"))
                .andRespond(withSuccess("{\"aprobado\":true,\"accion\":\"PERMITIR\"}", MediaType.APPLICATION_JSON));

        assertEquals(Veredicto.ENTREGABLE, filtro.verificar("hola"));
        servidor.verify();
    }

    @Test
    @DisplayName("manda la accion de la politica: BLOQUEAR, REVISION o RECHAZAR no entregan")
    void laAccionManda() {
        responde("{\"aprobado\":false,\"accion\":\"BLOQUEAR\",\"motivo\":\"x\"}");
        assertEquals(Veredicto.BLOQUEADO, filtro.verificar("uno"));

        servidor.reset();
        responde("{\"aprobado\":false,\"accion\":\"REVISION\"}");
        assertEquals(Veredicto.BLOQUEADO, filtro.verificar("dos"));

        servidor.reset();
        responde("{\"aprobado\":true,\"accion\":\"PERMITIR\",\"coincidencias\":[]}");
        assertEquals(Veredicto.ENTREGABLE, filtro.verificar("tres"));
    }

    @Test
    @DisplayName("un moderacion anterior a 2.0.0, sin accion, se lee por aprobado")
    void sinAccion() {
        responde("{\"aprobado\":false,\"motivo\":\"termino prohibido\"}");
        assertEquals(Veredicto.BLOQUEADO, filtro.verificar("uno"));

        servidor.reset();
        responde("{\"aprobado\":true}");
        assertEquals(Veredicto.ENTREGABLE, filtro.verificar("dos"));
    }

    @Test
    @DisplayName("sin respuesta, sin veredicto o con un 5xx: SIN_VERIFICAR, nunca «limpio»")
    void falloCerrado() {
        servidor.expect(requestTo(URL)).andRespond(withServerError());
        assertEquals(Veredicto.SIN_VERIFICAR, filtro.verificar("uno"));

        servidor.reset();
        responde("{}");
        assertEquals(Veredicto.SIN_VERIFICAR, filtro.verificar("dos"));

        servidor.reset();
        servidor.expect(requestTo(URL)).andRespond(withSuccess());
        assertEquals(Veredicto.SIN_VERIFICAR, filtro.verificar("tres"));
    }
}
