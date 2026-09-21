package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import com.nexusbattles.plataforma.resiliencia.EstadoDelCorta;
import com.nexusbattles.plataforma.resiliencia.RegistroDeDegradacion;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.ReservaDeCreditos;
import com.nexusbattles.plataforma.salaspartidas.configuracion.ConfiguracionDeResiliencia;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosInsuficientes;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosNoDisponibles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * El adaptador HTTP que de verdad habla con ms-finanzas — HU-JUE-014.
 *
 * <p>El transporte esta simulado; lo que importa no lo esta: las rutas y los
 * nombres de campo de {@code contracts/openapi/creditos.yaml}, la cabecera de
 * idempotencia, el mapeo de la respuesta y la traduccion de cada error a lo
 * que el caso de uso espera (422 con cifras, 503 sin inventar nada).
 */
@DisplayName("ClienteCreditos · el adaptador HTTP contra ms-finanzas")
class ClienteCreditosTest {

    private static final String BASE = "http://srv-ms-finanzas:8093/api/v1";
    private static final UUID JUGADOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SALA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RESERVA = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID GANADOR = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private MockRestServiceServer libro;
    private ClienteCreditos cliente;
    private RelojManual reloj;
    private CortaCircuitos corta;

    @BeforeEach
    void prepararElLibro() {
        RestClient.Builder constructor = RestClient.builder();
        libro = MockRestServiceServer.bindTo(constructor).build();
        reloj = new RelojManual();
        corta = new CortaCircuitos(ConfiguracionDeResiliencia.MS_FINANZAS,
                ConfiguracionDeResiliencia.SECCION_APUESTAS, 3, Duration.ofSeconds(30), reloj,
                new RegistroDeDegradacion());
        cliente = new ClienteCreditos(constructor.build(), BASE + "/", corta);
    }

    /** Reloj que solo avanza cuando la prueba lo dice: nada de dormir el hilo. */
    static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-09-21T10:00:00Z");

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private static String reservaJson(String estado) {
        return """
                {"reservaId":"%s","jugadorUid":"%s","monto":150.00,"estado":"%s",
                 "expiraEn":"2026-09-24T10:00:00Z"}
                """.formatted(RESERVA, JUGADOR, estado);
    }

    @Nested
    @DisplayName("reservar")
    class Reservar {

        @Test
        @DisplayName("manda el cuerpo del contrato con la clave de idempotencia y devuelve la reserva")
        void reservaSegunElContrato() {
            libro.expect(requestTo(BASE + "/creditos/reservar"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Idempotency-Key", "sala-" + SALA + "-jugador-" + JUGADOR + "-v3"))
                    .andExpect(jsonPath("$.jugadorUid").value(JUGADOR.toString()))
                    .andExpect(jsonPath("$.monto").value(150))
                    .andExpect(jsonPath("$.concepto").value("apuesta-sala"))
                    .andExpect(jsonPath("$.referenciaId").value("sala-" + SALA))
                    .andRespond(withStatus(HttpStatus.CREATED)
                            .contentType(MediaType.APPLICATION_JSON).body(reservaJson("ACTIVA")));

            ReservaDeCreditos reserva = cliente.reservar(JUGADOR, 150, SALA, 3);

            libro.verify();
            assertAll(
                    () -> assertEquals(RESERVA, reserva.id()),
                    () -> assertEquals(150, reserva.creditos()));
        }

        @Test
        @DisplayName("CA-02: un 422 se traduce a CreditosInsuficientes con el saldo consultado aparte")
        void saldoInsuficiente() {
            // HttpStatusCode.valueOf(422), no la constante: asi llega desde una
            // respuesta HTTP real, y en Spring 7 resuelve a UNPROCESSABLE_CONTENT,
            // no a UNPROCESSABLE_ENTITY. Con la constante esta prueba pasaba y el
            // E2E fallaba.
            libro.expect(requestTo(BASE + "/creditos/reservar"))
                    .andRespond(withStatus(HttpStatusCode.valueOf(422))
                            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                            .body("""
                                    {"type":"https://nexusbattles.upb.edu.co/errors/saldo-insuficiente",
                                     "title":"Saldo insuficiente","status":422,
                                     "detail":"Saldo insuficiente para reservar 150 créditos."}
                                    """));
            libro.expect(requestTo(BASE + "/creditos/" + JUGADOR + "/saldo"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("""
                            {"jugadorUid":"%s","saldoBruto":500.00,"saldoReservado":420.00,"saldoDisponible":80.00}
                            """.formatted(JUGADOR), MediaType.APPLICATION_JSON));

            CreditosInsuficientes error = assertThrows(CreditosInsuficientes.class,
                    () -> cliente.reservar(JUGADOR, 150, SALA, 0));

            libro.verify();
            assertAll(
                    () -> assertEquals(80, error.disponibles()),
                    () -> assertEquals(150, error.requeridos()),
                    () -> assertEquals(422, error.estado()));
        }

        @Test
        @DisplayName("si tras el 422 tampoco se puede leer el saldo, sigue siendo 422 (con 0 disponibles)")
        void saldoInsuficienteSinPoderLeerElSaldo() {
            libro.expect(requestTo(BASE + "/creditos/reservar"))
                    .andRespond(withStatus(HttpStatusCode.valueOf(422)));
            libro.expect(requestTo(BASE + "/creditos/" + JUGADOR + "/saldo"))
                    .andRespond(withServerError());

            CreditosInsuficientes error = assertThrows(CreditosInsuficientes.class,
                    () -> cliente.reservar(JUGADOR, 150, SALA, 0));

            assertEquals(0, error.disponibles());
        }

        @Test
        @DisplayName("CA-06 / HU-DIS-003: si el libro no responde, la seccion de apuestas queda degradada y nada se reserva")
        void libroCaido() {
            libro.expect(requestTo(BASE + "/creditos/reservar"))
                    .andRespond(withException(new IOException("connection refused")));

            DependenciaDegradada error = assertThrows(DependenciaDegradada.class,
                    () -> cliente.reservar(JUGADOR, 150, SALA, 0));

            assertAll(
                    () -> assertEquals(ConfiguracionDeResiliencia.MS_FINANZAS, error.dependencia()),
                    () -> assertEquals(ConfiguracionDeResiliencia.SECCION_APUESTAS, error.seccion()),
                    () -> assertTrue(error.getCause().getMessage().contains("connection refused"),
                            "la causa real viaja para la bitacora: " + error.getCause()));
        }

        @Test
        @DisplayName("un 500 del libro tambien es degradacion de este lado, no un 500 propio")
        void errorDelLibro() {
            libro.expect(requestTo(BASE + "/creditos/reservar")).andRespond(withServerError());

            assertThrows(DependenciaDegradada.class, () -> cliente.reservar(JUGADOR, 150, SALA, 0));
        }

        @Test
        @DisplayName("un 400 del libro (cuerpo invalido) no se confunde con saldo insuficiente")
        void rechazoDistintoDe422() {
            libro.expect(requestTo(BASE + "/creditos/reservar"))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST));

            CreditosNoDisponibles error = assertThrows(CreditosNoDisponibles.class,
                    () -> cliente.reservar(JUGADOR, 150, SALA, 0));

            assertTrue(error.detalle().contains("400"));
        }

        @Test
        @DisplayName("una respuesta sin identificador de reserva no se acepta")
        void respuestaIncompleta() {
            libro.expect(requestTo(BASE + "/creditos/reservar"))
                    .andRespond(withStatus(HttpStatus.CREATED)
                            .contentType(MediaType.APPLICATION_JSON).body("{\"estado\":\"ACTIVA\"}"));

            assertThrows(CreditosNoDisponibles.class, () -> cliente.reservar(JUGADOR, 150, SALA, 0));
        }

        @Test
        @DisplayName("no se reserva una cantidad no positiva: ni se llama al libro")
        void cantidadNoPositiva() {
            assertThrows(IllegalArgumentException.class, () -> cliente.reservar(JUGADOR, 0, SALA, 0));
            libro.verify();
        }
    }

    @Nested
    @DisplayName("liberar")
    class Liberar {

        @Test
        @DisplayName("hace POST a la ruta del contrato")
        void libera() {
            libro.expect(requestTo(BASE + "/creditos/reservas/" + RESERVA + "/liberar"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess(reservaJson("LIBERADA"), MediaType.APPLICATION_JSON));

            cliente.liberar(RESERVA);

            libro.verify();
        }

        @Test
        @DisplayName("si el libro no responde, propaga la degradacion: quien libera decide que hacer con ella")
        void noResponde() {
            libro.expect(requestTo(BASE + "/creditos/reservas/" + RESERVA + "/liberar"))
                    .andRespond(withException(new IOException("timeout")));

            DependenciaDegradada error = assertThrows(DependenciaDegradada.class, () -> cliente.liberar(RESERVA));

            assertEquals(ConfiguracionDeResiliencia.MS_FINANZAS, error.dependencia());
        }

        @Test
        @DisplayName("un 4xx al liberar es un desacuerdo con el libro, no una caida: 503 con la reserva en el detalle")
        void rechazada() {
            libro.expect(requestTo(BASE + "/creditos/reservas/" + RESERVA + "/liberar"))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND));

            CreditosNoDisponibles error = assertThrows(CreditosNoDisponibles.class, () -> cliente.liberar(RESERVA));

            assertAll(
                    () -> assertTrue(error.detalle().contains(RESERVA.toString())),
                    () -> assertTrue(error.detalle().contains("404")),
                    () -> assertEquals(EstadoDelCorta.CERRADO, corta.estado(), "un 4xx no abre el circuito"));
        }
    }

    // =====================================================================
    // HU-DIS-003: el corta circuitos protege a ESTE servicio, no al libro
    // =====================================================================

    @Nested
    @DisplayName("corta circuitos (HU-DIS-003)")
    class CortaCircuitosDelLibro {

        private void elLibroNoContesta(int veces) {
            for (int i = 0; i < veces; i++) {
                libro.expect(requestTo(BASE + "/creditos/reservar"))
                        .andRespond(withException(new IOException("connection refused")));
            }
        }

        @Test
        @DisplayName("tres fallos seguidos abren el circuito y la cuarta llamada ya no toca el libro")
        void seAbreTrasTresFallos() {
            elLibroNoContesta(3);

            for (int intento = 0; intento < 3; intento++) {
                assertThrows(DependenciaDegradada.class, () -> cliente.reservar(JUGADOR, 150, SALA, 0));
            }
            assertEquals(EstadoDelCorta.ABIERTO, corta.estado());

            // Sin expectativa para una cuarta peticion: si el cliente la hiciera,
            // el servidor simulado la rechazaria y esta prueba fallaria.
            DependenciaDegradada sinLlamar = assertThrows(DependenciaDegradada.class,
                    () -> cliente.reservar(JUGADOR, 150, SALA, 0));

            libro.verify();
            assertAll(
                    () -> assertEquals(ConfiguracionDeResiliencia.SECCION_APUESTAS, sinLlamar.seccion()),
                    () -> assertNull(sinLlamar.getCause(), "con el circuito abierto no hay llamada que falle"));
        }

        @Test
        @DisplayName("un 422 es una respuesta del libro, no un fallo: tres seguidos no abren nada")
        void elSaldoInsuficienteNoCuenta() {
            for (int i = 0; i < 3; i++) {
                libro.expect(requestTo(BASE + "/creditos/reservar"))
                        .andRespond(withStatus(HttpStatusCode.valueOf(422)));
                libro.expect(requestTo(BASE + "/creditos/" + JUGADOR + "/saldo"))
                        .andRespond(withSuccess("{\"saldoDisponible\":10.00}", MediaType.APPLICATION_JSON));
            }
            libro.expect(requestTo(BASE + "/creditos/reservar"))
                    .andRespond(withStatus(HttpStatus.CREATED)
                            .contentType(MediaType.APPLICATION_JSON).body(reservaJson("ACTIVA")));

            for (int intento = 0; intento < 3; intento++) {
                assertThrows(CreditosInsuficientes.class, () -> cliente.reservar(JUGADOR, 150, SALA, 0));
            }
            ReservaDeCreditos reserva = cliente.reservar(JUGADOR, 150, SALA, 0);

            libro.verify();
            assertAll(
                    () -> assertEquals(RESERVA, reserva.id()),
                    () -> assertEquals(EstadoDelCorta.CERRADO, corta.estado()));
        }

        @Test
        @DisplayName("pasada la espera deja pasar UNA llamada de prueba, y si el libro contesta el circuito se cierra")
        void seRecuperaCuandoElLibroVuelve() {
            elLibroNoContesta(3);
            libro.expect(requestTo(BASE + "/creditos/reservar"))
                    .andRespond(withStatus(HttpStatus.CREATED)
                            .contentType(MediaType.APPLICATION_JSON).body(reservaJson("ACTIVA")));

            for (int intento = 0; intento < 3; intento++) {
                assertThrows(DependenciaDegradada.class, () -> cliente.reservar(JUGADOR, 150, SALA, 0));
            }
            reloj.avanzar(Duration.ofSeconds(31));

            ReservaDeCreditos reserva = cliente.reservar(JUGADOR, 150, SALA, 0);

            libro.verify();
            assertAll(
                    () -> assertEquals(150, reserva.creditos()),
                    () -> assertEquals(EstadoDelCorta.CERRADO, corta.estado()));
        }
    }

    @Nested
    @DisplayName("consumir")
    class Consumir {

        @Test
        @DisplayName("cobra la reserva acreditandosela al beneficiario")
        void consume() {
            libro.expect(requestTo(BASE + "/creditos/reservas/" + RESERVA + "/consumir"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(jsonPath("$.vendedorUid").value(GANADOR.toString()))
                    .andRespond(withSuccess("""
                            {"reservaId":"%s","estado":"CONSUMIDA","montoDebitado":150.00,
                             "vendedorUid":"%s","transaccionId":"TX-CRED-1234ABCD"}
                            """.formatted(RESERVA, GANADOR), MediaType.APPLICATION_JSON));

            cliente.consumir(RESERVA, GANADOR);

            libro.verify();
        }

        @Test
        @DisplayName("sin beneficiario manda vendedorUid nulo (la casa se queda con la apuesta)")
        void sinBeneficiario() {
            libro.expect(requestTo(BASE + "/creditos/reservas/" + RESERVA + "/consumir"))
                    .andExpect(content().json("{\"vendedorUid\":null}"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            cliente.consumir(RESERVA, null);

            libro.verify();
        }

        @Test
        @DisplayName("un 409 (reserva ya liberada) es un desacuerdo con el libro: se propaga, no se tapa")
        void yaLiberada() {
            libro.expect(requestTo(BASE + "/creditos/reservas/" + RESERVA + "/consumir"))
                    .andRespond(withStatus(HttpStatus.CONFLICT)
                            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                            .body("{\"type\":\"https://nexusbattles.upb.edu.co/errors/reserva-ya-liberada\",\"status\":409}"));

            assertThrows(CreditosNoDisponibles.class, () -> cliente.consumir(RESERVA, GANADOR));
        }
    }

    // =====================================================================
    // Contrato: las rutas que usa este cliente existen en creditos.yaml
    // =====================================================================

    @Test
    @DisplayName("cada ruta que compone el cliente esta declarada en contracts/openapi/creditos.yaml")
    void lasRutasEstanEnElContrato() throws IOException {
        Path contrato = Path.of("..", "..", "..", "contracts", "openapi", "creditos.yaml");
        String yaml = Files.readString(contrato, StandardCharsets.UTF_8);

        assertAll(
                () -> assertTrue(yaml.contains("/creditos/{uid}/saldo:")),
                () -> assertTrue(yaml.contains("/creditos/reservar:")),
                () -> assertTrue(yaml.contains("/creditos/reservas/{reservaId}/liberar:")),
                () -> assertTrue(yaml.contains("/creditos/reservas/{reservaId}/consumir:")),
                () -> assertTrue(yaml.contains("name: Idempotency-Key")),
                () -> assertTrue(yaml.contains("saldo-insuficiente")),
                () -> assertTrue(yaml.contains("reserva-ya-liberada")));
    }
}
