package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaInventarioDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// HU-CHA-008 y 1.5.0 (7.4.4): el inventario, y la busqueda en el, siempre con
// el token del propio jugador (inventario.yaml, el dueno sale del claim uid).
class InventarioClientHttpTest {

    private static final String PAGINA = "{\"elementos\":[{\"nombrePropio\":\"Espada del Alba\",\"tipo\":\"ARMA\","
        + "\"equipado\":true}],\"totalElementos\":1}";

    @Test
    void consultaElInventarioConElTokenDelJugador() {
        RestClient.Builder constructor = RestClient.builder().baseUrl("http://inventario");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        servidor.expect(requestTo("http://inventario/api/v1/inventario/elementos?pagina=0"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer token-del-jugador"))
            .andRespond(withSuccess(PAGINA, MediaType.APPLICATION_JSON));

        PaginaInventarioDto pagina = new InventarioClientHttp(constructor.build())
            .consultarInventario("token-del-jugador", 0);

        assertThat(pagina.totalElementos()).isEqualTo(1);
        servidor.verify();
    }

    // El criterio viaja como parametro de consulta codificado: un texto con
    // espacios, tildes o «&» no puede anadir parametros a la peticion.
    @Test
    void buscaConElCriterioCodificadoYElTokenDelJugador() {
        RestClient.Builder constructor = RestClient.builder().baseUrl("http://inventario");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        servidor.expect(requestTo(
                "http://inventario/api/v1/inventario/elementos/busqueda?criterio=mago%20de%20fuego%26pagina%3D9&pagina=0"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer token-del-jugador"))
            .andRespond(withSuccess(PAGINA, MediaType.APPLICATION_JSON));

        PaginaInventarioDto pagina = new InventarioClientHttp(constructor.build())
            .buscar("token-del-jugador", "mago de fuego&pagina=9");

        assertThat(pagina.elementos()).singleElement()
            .satisfies(e -> assertThat(e.nombrePropio()).isEqualTo("Espada del Alba"));
        servidor.verify();
    }
}
