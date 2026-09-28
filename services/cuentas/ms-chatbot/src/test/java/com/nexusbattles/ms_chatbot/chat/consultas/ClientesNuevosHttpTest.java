package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaMovimientosDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.TorneoDetalleDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// B11: torneos se consulta como dato publico (sin credencial); los
// movimientos, con el token del propio jugador (nunca uno de servicio).
class ClientesNuevosHttpTest {

    @Test
    void torneosSinNingunaCredencial() {
        RestClient.Builder constructor = RestClient.builder().baseUrl("http://torneos/api/v1");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        UUID id = UUID.randomUUID();
        servidor.expect(requestTo("http://torneos/api/v1/torneos"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(headerDoesNotExist("Authorization"))
            .andRespond(withSuccess("[{\"id\":\"" + id + "\",\"nombre\":\"Copa\",\"estado\":\"EN_CURSO\","
                + "\"costoInscripcion\":10,\"equiposInscritos\":8,\"cupos\":8,\"extra\":1}]", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://torneos/api/v1/torneos/" + id))
            .andExpect(headerDoesNotExist("Authorization"))
            .andRespond(withSuccess("{\"id\":\"" + id + "\",\"nombre\":\"Copa\",\"estado\":\"EN_CURSO\",\"equipos\":[],"
                + "\"encuentros\":[{\"numero\":1,\"estado\":\"LISTO\"}]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://torneos/api/v1/torneos"))
            .andRespond(withSuccess());

        TorneosClientHttp cliente = new TorneosClientHttp(constructor.build());
        assertThat(cliente.listar()).singleElement().satisfies(t -> assertThat(t.nombre()).isEqualTo("Copa"));
        TorneoDetalleDto detalle = cliente.obtener(id);
        assertThat(detalle.encuentros()).singleElement().satisfies(e -> assertThat(e.estado()).isEqualTo("LISTO"));
        assertThat(cliente.listar()).isEmpty();
        servidor.verify();
    }

    @Test
    void movimientosConElTokenDelJugador() {
        RestClient.Builder constructor = RestClient.builder().baseUrl("http://finanzas/api/v1");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        servidor.expect(requestTo("http://finanzas/api/v1/creditos/uid-1/movimientos?page=0&size=5"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer token-del-jugador"))
            .andRespond(withSuccess("{\"content\":[{\"monto\":2,\"concepto\":\"recompensa-victoria\",\"signo\":\"SUMA\"}],"
                + "\"totalElements\":1}", MediaType.APPLICATION_JSON));

        PaginaMovimientosDto pagina = new FinanzasClientHttp(constructor.build()).movimientos("token-del-jugador", "uid-1", 5);
        assertThat(pagina.totalElements()).isEqualTo(1);
        assertThat(pagina.content()).singleElement().satisfies(m -> assertThat(m.signo()).isEqualTo("SUMA"));
        servidor.verify();
    }
}
