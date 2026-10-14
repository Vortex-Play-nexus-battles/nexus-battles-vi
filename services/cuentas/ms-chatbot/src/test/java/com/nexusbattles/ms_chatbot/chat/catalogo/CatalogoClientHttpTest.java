package com.nexusbattles.ms_chatbot.chat.catalogo;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 1.10.0: el catalogo se lee como lo ve un visitante, sin credencial (la
// proyeccion publica de productos.yaml 1.4.0, sin campos internos).
class CatalogoClientHttpTest {

    @Test
    void pideUnaPaginaSinNingunaCredencial() {
        RestClient.Builder constructor = RestClient.builder().baseUrl("http://productos");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        servidor.expect(requestTo("http://productos/api/v1/productos?page=0&size=50"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(headerDoesNotExist("Authorization"))
            .andRespond(withSuccess("{\"content\":[{\"id\":\"p-1\",\"nombre\":\"Espada de una mano\",\"tipo\":\"ARMA\","
                + "\"descripcion\":\"+1 ataque\",\"precioCreditos\":120,\"premium\":false,\"estado\":\"ACTIVO\","
                + "\"promocion\":{\"porcentaje\":20,\"hasta\":\"2026-10-20T00:00:00Z\",\"vigente\":true}}],"
                + "\"page\":0,\"size\":50,\"totalElements\":1,\"totalPages\":1}", MediaType.APPLICATION_JSON));

        PaginaDelCatalogoDto pagina = new CatalogoClientHttp(constructor.build()).pagina(0, 50);

        assertThat(pagina.totalPages()).isEqualTo(1);
        assertThat(pagina.content()).singleElement().satisfies(p -> {
            assertThat(p.nombre()).isEqualTo("Espada de una mano");
            assertThat(p.precioCreditos()).isEqualTo(120);
            assertThat(p.promocion().porcentaje()).isEqualTo(20);
        });
        servidor.verify();
    }
}
