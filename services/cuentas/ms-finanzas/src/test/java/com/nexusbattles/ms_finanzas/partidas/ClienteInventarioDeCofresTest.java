package com.nexusbattles.ms_finanzas.partidas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * El adaptador contra {@code POST /api/v1/inventario/entregas} (inventario.yaml
 * 1.4.0): lo que manda y cómo traduce cada respuesta.
 */
@DisplayName("ClienteInventarioDeCofres · entrega del cofre por inventario.yaml 1.4.0")
class ClienteInventarioDeCofresTest {

    private static final String BASE = "http://inventario:8080";
    private static final String UID = "11111111-1111-1111-1111-111111111111";

    private MockRestServiceServer inventario;
    private ClienteInventarioDeCofres cliente;
    private CofreEntregado cofre;

    @BeforeEach
    void setUp() {
        RestClient.Builder constructor = RestClient.builder();
        inventario = MockRestServiceServer.bindTo(constructor).build();
        cliente = new ClienteInventarioDeCofres(constructor.build(), BASE + "/");
        cofre = CofreEntregado.sorteado(UID, "2026-W38", Instant.parse("2026-09-16T10:00:00Z"), 42L,
                TablaDeCofre.desde("PRUEBA-1", "1647b2ea-096d-37e7-b580-0172e4c62313=1"));
    }

    @Test
    @DisplayName("manda uid, origen COFRE, la referencia y los premios, con Idempotency-Key cofre-{id}")
    void mandaLoQueDiceElContrato() {
        inventario.expect(requestTo(BASE + "/api/v1/inventario/entregas"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "cofre-" + cofre.getId()))
                .andExpect(jsonPath("$.uid").value(UID))
                .andExpect(jsonPath("$.origen").value("COFRE"))
                .andExpect(jsonPath("$.referencia").value("cofre-" + cofre.getId()))
                .andExpect(jsonPath("$.productos[0].productoId").value("1647b2ea-096d-37e7-b580-0172e4c62313"))
                .andExpect(jsonPath("$.productos[0].cantidad").value(1))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"entrega-9\",\"uid\":\"" + UID + "\",\"origen\":\"COFRE\","
                                + "\"referencia\":\"x\",\"elementos\":[],\"entregadaEn\":\"2026-09-16T10:00:01Z\"}"));

        assertThat(cliente.entregar(cofre)).isEqualTo("entrega-9");
        inventario.verify();
    }

    @Test
    @DisplayName("la misma clave repetida devuelve la entrega original (200): se toma igual")
    void repetidaEsLaMisma() {
        inventario.expect(requestTo(BASE + "/api/v1/inventario/entregas"))
                .andRespond(withSuccess("{\"id\":\"entrega-9\"}", MediaType.APPLICATION_JSON));

        assertThat(cliente.entregar(cofre)).isEqualTo("entrega-9");
    }

    @Test
    @DisplayName("un 404 (ruta de B4 aún no desplegada), un 409 o un 5xx dejan la entrega sin hacer, con el motivo")
    void rechazos() {
        inventario.expect(requestTo(BASE + "/api/v1/inventario/entregas")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        inventario.expect(requestTo(BASE + "/api/v1/inventario/entregas")).andRespond(withStatus(HttpStatus.CONFLICT));
        inventario.expect(requestTo(BASE + "/api/v1/inventario/entregas")).andRespond(withServerError());

        assertThatThrownBy(() -> cliente.entregar(cofre)).isInstanceOf(InventarioDeCofres.EntregaNoRealizada.class)
                .hasMessageContaining("404");
        assertThatThrownBy(() -> cliente.entregar(cofre)).isInstanceOf(InventarioDeCofres.EntregaNoRealizada.class)
                .hasMessageContaining("409");
        assertThatThrownBy(() -> cliente.entregar(cofre)).isInstanceOf(InventarioDeCofres.EntregaNoRealizada.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("una respuesta sin identificador de entrega no es una entrega hecha")
    void sinIdentificador() {
        inventario.expect(requestTo(BASE + "/api/v1/inventario/entregas"))
                .andRespond(withSuccess("{\"uid\":\"" + UID + "\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.entregar(cofre)).isInstanceOf(InventarioDeCofres.EntregaNoRealizada.class)
                .hasMessageContaining("sin identificador");
    }

    @Test
    @DisplayName("sin credencial de servicio (el interceptor falla) la entrega queda sin hacer, no revienta")
    void sinCredencial() {
        RestClient conCredencialCaida = RestClient.builder()
                .requestInterceptor((peticion, cuerpo, ejecucion) -> {
                    throw new IllegalStateException("emisor de credenciales caido");
                })
                .build();

        assertThatThrownBy(() -> new ClienteInventarioDeCofres(conCredencialCaida, BASE).entregar(cofre))
                .isInstanceOf(InventarioDeCofres.EntregaNoRealizada.class)
                .hasMessageContaining("emisor de credenciales caido");
    }

    @Test
    @DisplayName("sin INVENTARIO_BASE_URL no se llama a nadie y el cofre queda pendiente")
    void sinUrl() {
        assertThatThrownBy(() -> new ClienteInventarioDeCofres(RestClient.create(), "").entregar(cofre))
                .isInstanceOf(InventarioDeCofres.EntregaNoRealizada.class)
                .hasMessageContaining("INVENTARIO_BASE_URL");
        assertThatThrownBy(() -> new ClienteInventarioDeCofres(RestClient.create(), null).entregar(cofre))
                .isInstanceOf(InventarioDeCofres.EntregaNoRealizada.class);
    }
}
