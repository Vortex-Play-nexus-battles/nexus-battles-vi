package com.nexusbattles.plataforma.torneos.integracion;

import com.nexusbattles.plataforma.torneos.torneo.TorneoRechazado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Los tres clientes contra las formas exactas de sus contratos. */
@DisplayName("Torneos · clientes HTTP")
class ClientesHttpTest {

    private final RestClient.Builder constructor = RestClient.builder();
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
    private final RestClient http = constructor.build();

    @Test
    @DisplayName("reservar manda la clave idempotente y el concepto del torneo; 422 es CREDITOS_INSUFICIENTES; caido es LIBRO_NO_DISPONIBLE")
    void creditos() {
        ClienteCreditos libro = new ClienteCreditos(http, "http://finanzas/api/v1/");
        UUID jugador = UUID.randomUUID();
        UUID reserva = UUID.randomUUID();
        servidor.expect(requestTo("http://finanzas/api/v1/creditos/reservar"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "torneo-1-equipo-2"))
                .andExpect(content().json("{\"jugadorUid\":\"" + jugador + "\",\"monto\":30,\"concepto\":\"inscripcion-torneo\",\"referenciaId\":\"torneo-1\"}"))
                .andRespond(withSuccess("{\"reservaId\":\"" + reserva + "\",\"monto\":30,\"estado\":\"ACTIVA\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://finanzas/api/v1/creditos/reservar"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"title\":\"Saldo insuficiente\"}"));
        servidor.expect(requestTo("http://finanzas/api/v1/creditos/reservas/" + reserva + "/consumir"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"transaccionId\":\"TX-1\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://finanzas/api/v1/creditos/reservas/" + reserva + "/liberar"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(libro.reservar(jugador, 30, "torneo-1-equipo-2", "torneo-1")).isEqualTo(reserva);
        assertThatThrownBy(() -> libro.reservar(jugador, 30, "k", "r"))
                .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(TorneoRechazado.Motivo.CREDITOS_INSUFICIENTES));
        libro.consumir(reserva);
        assertThatThrownBy(() -> libro.liberar(reserva))
                .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(TorneoRechazado.Motivo.LIBRO_NO_DISPONIBLE));
        servidor.verify();
    }

    @Test
    @DisplayName("la lista negra decide sobre el texto; sin respuesta no se aprueba nada")
    void listaNegra() {
        ClienteListaNegra filtro = new ClienteListaNegra(http, "http://moderacion/api/v1/lista-negra/verificar");
        servidor.expect(requestTo("http://moderacion/api/v1/lista-negra/verificar"))
                .andExpect(content().json("{\"texto\":\"Los Valientes\"}"))
                .andRespond(withSuccess("{\"aprobado\":true}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://moderacion/api/v1/lista-negra/verificar"))
                .andRespond(withSuccess("{\"aprobado\":false,\"motivo\":\"termino prohibido\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://moderacion/api/v1/lista-negra/verificar"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThat(filtro.aprobado("Los Valientes")).isTrue();
        assertThat(filtro.aprobado("Los Groseros")).isFalse();
        assertThatThrownBy(() -> filtro.aprobado("x"))
                .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(TorneoRechazado.Motivo.LISTA_NEGRA_NO_DISPONIBLE));
        servidor.verify();
    }

    @Test
    @DisplayName("la consulta de sancion activa es fail-closed")
    void sanciones() {
        ClienteSanciones consulta = new ClienteSanciones(http, "http://moderacion/api/v1");
        UUID jugador = UUID.randomUUID();
        servidor.expect(requestTo("http://moderacion/api/v1/sanciones/usuarios/" + jugador + "/activa"))
                .andRespond(withSuccess("{\"sancionActiva\":true,\"tipo\":\"SUSPENSION\",\"vigenteHasta\":\"2026-10-02T00:00:00Z\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://moderacion/api/v1/sanciones/usuarios/" + jugador + "/activa"))
                .andRespond(withSuccess("{\"sancionActiva\":false}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://moderacion/api/v1/sanciones/usuarios/" + jugador + "/activa"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(consulta.sancionado(jugador)).isTrue();
        assertThat(consulta.sancionado(jugador)).isFalse();
        assertThatThrownBy(() -> consulta.sancionado(jugador))
                .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(TorneoRechazado.Motivo.SANCIONES_NO_DISPONIBLES));
        servidor.verify();
    }
}
