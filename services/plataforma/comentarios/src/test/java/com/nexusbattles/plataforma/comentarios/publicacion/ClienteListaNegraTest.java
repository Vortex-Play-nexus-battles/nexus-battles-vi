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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.nexusbattles.plataforma.comentarios.HiloDeComentarios.ResultadoDelFiltro;

/**
 * Pruebas del cliente de la lista negra contra el contrato de HU-ADM-002.
 *
 * Lo que mas importa verificar aqui es el respaldo. Para los apodos se decidio
 * dejar pasar cuando el servicio no responde, pero la postcondicion de RF-COM-007 es que el
 * contenido inapropiado no alcance la publicacion sin revision, asi que
 * publicar sin verificar rompe el requisito. Lo unico que no lo rompe es retener el comentario
 * para que lo revise un moderador, y eso es lo que se prueba en los dos casos
 * de falla.
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
    @DisplayName("un texto aprobado por la lista negra sale limpio")
    void textoAprobadoSaleLimpio() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("{\"aprobado\":true}", MediaType.APPLICATION_JSON));

        assertEquals(ResultadoDelFiltro.LIMPIO, cliente.verificar("Muy buena espada"));
        servidor.verify();
    }

    @Test
    @DisplayName("un texto rechazado por la lista negra queda senalado")
    void textoRechazadoQuedaSenalado() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"aprobado\":false,\"motivo\":\"termino prohibido\"}",
                        MediaType.APPLICATION_JSON));

        assertEquals(ResultadoDelFiltro.SENALADO, cliente.verificar("texto con groseria"));
        servidor.verify();
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
    @DisplayName("el texto a verificar viaja en el cuerpo con el nombre que fija el contrato")
    void elTextoViajaEnElCuerpoDelContrato() {
        servidor.expect(requestTo(URL))
                .andExpect(content().json("{\"texto\":\"Muy buena espada\"}"))
                .andRespond(withSuccess("{\"aprobado\":true}", MediaType.APPLICATION_JSON));

        cliente.verificar("Muy buena espada");
        servidor.verify();
    }
}
