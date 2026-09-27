package com.nexusbattles.plataforma.comentarios.publicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.nexusbattles.plataforma.comentarios.HiloDeComentarios.ResultadoDelFiltro;

/**
 * Pruebas del cliente de la lista negra contra el contrato de HU-ADM-002
 * (moderacion-lista-negra 2.0.0 desde B3: {@code contexto} y {@code accion}).
 *
 * Lo que mas importa verificar aqui es el respaldo. Para los apodos se decidio
 * dejar pasar cuando el servicio no responde, pero la postcondicion de RF-COM-007 es que el
 * contenido inapropiado no alcance la publicacion sin revision, asi que
 * publicar sin verificar rompe el requisito. Lo unico que no lo rompe es retener el comentario
 * para que lo revise un moderador, y eso es lo que se prueba en los casos de falla.
 */
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
    @DisplayName("la accion PERMITIR publica")
    void permitirPublica() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("{\"aprobado\":true,\"accion\":\"PERMITIR\"}", MediaType.APPLICATION_JSON));

        assertEquals(ResultadoDelFiltro.LIMPIO, cliente.verificar("Muy buena espada"));
        servidor.verify();
    }

    @Test
    @DisplayName("la accion REVISION (la de COMENTARIO por politica) retiene en revision")
    void revisionRetiene() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"aprobado\":false,\"accion\":\"REVISION\",\"motivo\":\"termino prohibido\","
                                + "\"categoria\":\"OFENSIVO\",\"coincidencias\":[\"x\"]}",
                        MediaType.APPLICATION_JSON));

        assertEquals(ResultadoDelFiltro.SENALADO, cliente.verificar("texto con groseria"));
        servidor.verify();
    }

    @ParameterizedTest(name = "la accion {0} tambien retiene: nada inapropiado sale publicado")
    @ValueSource(strings = {"RECHAZAR", "BLOQUEAR", "UNA_NUEVA"})
    void otrasAccionesRetienen(String accion) {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("{\"aprobado\":false,\"accion\":\"" + accion + "\"}",
                        MediaType.APPLICATION_JSON));

        assertEquals(ResultadoDelFiltro.SENALADO, cliente.verificar("texto"));
    }

    @Test
    @DisplayName("la accion manda aunque aprobado diga otra cosa")
    void laAccionManda() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("{\"aprobado\":true,\"accion\":\"REVISION\"}", MediaType.APPLICATION_JSON));

        assertEquals(ResultadoDelFiltro.SENALADO, cliente.verificar("texto"));
    }

    @Test
    @DisplayName("un servidor anterior a la 2.0.0, sin accion, se entiende por aprobado")
    void servidorSinAccion() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("{\"aprobado\":true}", MediaType.APPLICATION_JSON));
        assertEquals(ResultadoDelFiltro.LIMPIO, cliente.verificar("Muy buena espada"));

        servidor.reset();
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("{\"aprobado\":false,\"motivo\":\"termino prohibido\"}",
                        MediaType.APPLICATION_JSON));
        assertEquals(ResultadoDelFiltro.SENALADO, cliente.verificar("texto con groseria"));
    }

    @Test
    @DisplayName("si el servicio de lista negra falla, el comentario se retiene en vez de publicarse")
    void siElServicioFallaSeRetiene() {
        servidor.expect(requestTo(URL)).andRespond(withServerError());

        assertEquals(ResultadoDelFiltro.SENALADO, cliente.verificar("da igual el texto"));
        servidor.verify();
    }

    @Test
    @DisplayName("una respuesta vacia tambien retiene, no se asume que estaba limpio")
    void respuestaVaciaTambienRetiene() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertEquals(ResultadoDelFiltro.SENALADO, cliente.verificar("da igual el texto"));
        servidor.verify();
    }

    @Test
    @DisplayName("el texto viaja con contexto COMENTARIO, con los nombres que fija el contrato 2.0.0")
    void elTextoViajaConSuContexto() {
        servidor.expect(requestTo(URL))
                .andExpect(content().json("{\"texto\":\"Muy buena espada\",\"contexto\":\"COMENTARIO\"}",
                        JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"aprobado\":true,\"accion\":\"PERMITIR\"}", MediaType.APPLICATION_JSON));

        cliente.verificar("Muy buena espada");
        servidor.verify();
    }
}
