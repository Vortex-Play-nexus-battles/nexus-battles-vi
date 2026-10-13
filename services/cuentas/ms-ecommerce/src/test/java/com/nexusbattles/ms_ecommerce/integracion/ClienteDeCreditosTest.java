package com.nexusbattles.ms_ecommerce.integracion;

import com.nexusbattles.ms_ecommerce.integracion.finanzas.ClienteDeCreditos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * D-44: el libro de creditos de ms-finanzas desde la tienda (creditos.yaml),
 * con el mismo constructor que se despliega.
 *
 * <p>Lo que se fija es la misma frontera que en los demas clientes: «ms-finanzas
 * decidio» (cobrado, saldo insuficiente, no hay nada que devolver) frente a
 * «ms-finanzas no respondio» (una excepcion que deja la orden donde esta para
 * reintentar con el mismo {@code refId}). Confundirlas seria rechazar una
 * compra que si se cobro, o cobrar dos veces.
 */
@DisplayName("Cliente del libro de creditos (ms-finanzas): decisiones frente a averias")
class ClienteDeCreditosTest {

    private static final String BASE = "http://finanzas.test/api/v1";
    private static final PropiedadesDeLaTienda.Http TIEMPOS =
            new PropiedadesDeLaTienda.Http(Duration.ofSeconds(2), Duration.ofSeconds(5));
    private static final String JUGADOR = "7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55";
    private static final String TIPO = "https://nexusbattles.upb.edu.co/errors/";

    private MockRestServiceServer servidor;

    private ClienteDeCreditos cliente() {
        RestClient.Builder constructor = ConfiguracionDeIntegraciones.constructor(BASE, TIEMPOS);
        servidor = MockRestServiceServer.bindTo(constructor).build();
        return new ClienteDeCreditos(constructor.build());
    }

    @AfterEach
    void todoLoEsperado() {
        if (servidor != null) {
            servidor.verify();
        }
    }

    private static String problema(String tipo, int estado) {
        return "{\"type\":\"" + TIPO + tipo + "\",\"status\":" + estado + ",\"detail\":\"-\"}";
    }

    @Nested
    @DisplayName("debitar: el cobro")
    class Debitar {

        @Test
        @DisplayName("200: cobrado; manda el uid, los creditos enteros, el refId de la orden y el concepto")
        void cobrado() {
            ClienteDeCreditos creditos = cliente();
            servidor.expect(requestTo(BASE + "/creditos/debitar"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(jsonPath("$.uid").value(JUGADOR))
                    .andExpect(jsonPath("$.monto").value(450))
                    .andExpect(jsonPath("$.refId").value("tienda-orden-1"))
                    .andExpect(jsonPath("$.concepto").value("Compra en la tienda: Hacha"))
                    .andRespond(withSuccess("""
                            {"transaccionId":"TX-DEB-1A2B3C4D","refId":"tienda-orden-1","estado":"EXITOSO",\
                            "montoDebitado":450,"nuevoSaldoDisponible":50}""", MediaType.APPLICATION_JSON));

            ClienteDeCreditos.ResultadoDelCobro resultado =
                    creditos.debitar(JUGADOR, 450, "tienda-orden-1", "Compra en la tienda: Hacha");

            assertThat(resultado).isEqualTo(new ClienteDeCreditos.Cobrado("TX-DEB-1A2B3C4D"));
        }

        @Test
        @DisplayName("422 saldo-insuficiente: la decision de ms-finanzas, no una averia")
        void saldoInsuficiente() {
            ClienteDeCreditos creditos = cliente();
            servidor.expect(requestTo(BASE + "/creditos/debitar")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problema("saldo-insuficiente", 422)));

            assertThat(creditos.debitar(JUGADOR, 450, "tienda-orden-1", "c"))
                    .isInstanceOf(ClienteDeCreditos.SaldoInsuficiente.class);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {400, 401, 403, 404, 409, 422, 500, 503})
        @DisplayName("cualquier otra respuesta es averia: la orden sigue PENDIENTE y se reintenta con el mismo refId")
        void averia(int estado) {
            ClienteDeCreditos creditos = cliente();
            servidor.expect(requestTo(BASE + "/creditos/debitar"))
                    .andRespond(withStatus(HttpStatus.valueOf(estado)).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                            .body(problema("otra-cosa", estado)));

            assertThatThrownBy(() -> creditos.debitar(JUGADOR, 450, "tienda-orden-1", "c"))
                    .isInstanceOfSatisfying(ServicioNoDisponibleException.class,
                            e -> assertThat(e.servicio()).isEqualTo("ms-finanzas"));
        }

        @Test
        @DisplayName("sin respuesta (tiempo agotado): averia, no se sabe si cobro — el refId lo resuelve al reintentar")
        void sinRespuesta() {
            ClienteDeCreditos creditos = cliente();
            servidor.expect(requestTo(BASE + "/creditos/debitar"))
                    .andRespond(withException(new SocketTimeoutException("Read timed out")));

            assertThatThrownBy(() -> creditos.debitar(JUGADOR, 450, "tienda-orden-1", "c"))
                    .isInstanceOf(ServicioNoDisponibleException.class);
        }
    }

    @Nested
    @DisplayName("reversar: la devolucion de una compra que no se pudo entregar")
    class Reversar {

        @Test
        @DisplayName("200 (REVERSADO o YA_REVERSADO): devuelto; manda el refId y el motivo")
        void devuelto() {
            ClienteDeCreditos creditos = cliente();
            for (String estado : new String[] {"REVERSADO", "YA_REVERSADO"}) {
                servidor.expect(requestTo(BASE + "/creditos/reversar"))
                        .andExpect(method(HttpMethod.POST))
                        .andExpect(jsonPath("$.refId").value("tienda-orden-1"))
                        .andExpect(jsonPath("$.motivo").value("Se agoto"))
                        .andRespond(withSuccess("{\"refId\":\"tienda-orden-1\",\"estado\":\"" + estado
                                + "\",\"montoReversado\":450}", MediaType.APPLICATION_JSON));
            }

            assertThat(creditos.reversar("tienda-orden-1", "Se agoto")).isEqualTo(ClienteDeCreditos.Devolucion.DEVUELTO);
            assertThat(creditos.reversar("tienda-orden-1", "Se agoto")).isEqualTo(ClienteDeCreditos.Devolucion.DEVUELTO);
        }

        @Test
        @DisplayName("404 reserva-no-encontrada: no hubo cobro con ese refId, no hay nada que devolver")
        void nadaQueDevolver() {
            ClienteDeCreditos creditos = cliente();
            servidor.expect(requestTo(BASE + "/creditos/reversar")).andRespond(withStatus(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema("reserva-no-encontrada", 404)));

            assertThat(creditos.reversar("tienda-orden-1", "m")).isEqualTo(ClienteDeCreditos.Devolucion.NADA_QUE_DEVOLVER);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {400, 401, 404, 500, 503})
        @DisplayName("un 404 sin su tipo (una ruta mal escrita) o cualquier otra cosa: averia, se reintenta")
        void averia(int estado) {
            ClienteDeCreditos creditos = cliente();
            servidor.expect(requestTo(BASE + "/creditos/reversar")).andRespond(withStatus(HttpStatus.valueOf(estado)));

            assertThatThrownBy(() -> creditos.reversar("tienda-orden-1", "m"))
                    .isInstanceOf(ServicioNoDisponibleException.class);
        }
    }

    @Nested
    @DisplayName("operacion: conciliar una orden que no supo si cobro")
    class Operacion {

        @Test
        @DisplayName("200: el debito existe, con su estado (CONSUMIDA = cobrado)")
        void existe() {
            ClienteDeCreditos creditos = cliente();
            servidor.expect(requestTo(BASE + "/creditos/operaciones/tienda-orden-1"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("""
                            {"refId":"tienda-orden-1","uid":"%s","monto":450,"concepto":"c","estado":"CONSUMIDA",\
                            "fecha":"2026-10-04T12:00:00Z"}""".formatted(JUGADOR), MediaType.APPLICATION_JSON));

            assertThat(creditos.operacion("tienda-orden-1"))
                    .hasValueSatisfying(op -> {
                        assertThat(op.cobrada()).isTrue();
                        assertThat(op.uid()).isEqualTo(JUGADOR);
                    });
        }

        @Test
        @DisplayName("200 LIBERADA: existio y ya se devolvio; no cuenta como cobrada")
        void devuelta() {
            ClienteDeCreditos creditos = cliente();
            servidor.expect(requestTo(BASE + "/creditos/operaciones/tienda-orden-1")).andRespond(withSuccess(
                    "{\"refId\":\"tienda-orden-1\",\"monto\":450,\"estado\":\"LIBERADA\"}", MediaType.APPLICATION_JSON));

            assertThat(creditos.operacion("tienda-orden-1")).hasValueSatisfying(op -> assertThat(op.cobrada()).isFalse());
        }

        @Test
        @DisplayName("404 reserva-no-encontrada: nunca se cobro")
        void noExiste() {
            ClienteDeCreditos creditos = cliente();
            servidor.expect(requestTo(BASE + "/creditos/operaciones/tienda-orden-1")).andRespond(
                    withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                            .body(problema("reserva-no-encontrada", 404)));

            assertThat(creditos.operacion("tienda-orden-1")).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {401, 404, 500, 503})
        @DisplayName("sin respuesta clara: averia; no se concluye que no cobro")
        void averia(int estado) {
            ClienteDeCreditos creditos = cliente();
            servidor.expect(requestTo(BASE + "/creditos/operaciones/tienda-orden-1"))
                    .andRespond(withStatus(HttpStatus.valueOf(estado)));

            assertThatThrownBy(() -> creditos.operacion("tienda-orden-1"))
                    .isInstanceOf(ServicioNoDisponibleException.class);
        }
    }

    @Nested
    @DisplayName("saldo: para la cotizacion")
    class Saldo {

        @Test
        @DisplayName("200: el saldo disponible, en creditos enteros hacia abajo")
        void saldo() {
            ClienteDeCreditos creditos = cliente();
            servidor.expect(requestTo(BASE + "/creditos/" + JUGADOR + "/saldo"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("""
                            {"jugadorUid":"%s","saldoBruto":620.75,"saldoReservado":100,"saldoDisponible":520.75}"""
                            .formatted(JUGADOR), MediaType.APPLICATION_JSON));

            assertThat(creditos.saldoDisponible(JUGADOR)).hasValue(520);
        }

        @Test
        @DisplayName("si ms-finanzas no responde, la cotizacion sale sin saldo (vacio), sin excepcion")
        void sinSaldo() {
            ClienteDeCreditos creditos = cliente();
            servidor.expect(requestTo(BASE + "/creditos/" + JUGADOR + "/saldo"))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
            servidor.expect(requestTo(BASE + "/creditos/" + JUGADOR + "/saldo"))
                    .andRespond(withException(new SocketTimeoutException("Read timed out")));

            assertThat(creditos.saldoDisponible(JUGADOR)).isEmpty();
            assertThat(creditos.saldoDisponible(JUGADOR)).isEmpty();
        }
    }
}
