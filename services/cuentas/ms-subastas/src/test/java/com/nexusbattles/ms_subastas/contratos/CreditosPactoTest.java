package com.nexusbattles.ms_subastas.contratos;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientException;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientHttp;
import com.nexusbattles.ms_subastas.pujas.creditos.ReservaCredito;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pacto de consumidor con ms-finanzas (regla 1 de plataforma: contrato primero;
 * y el riesgo #3 del acta, contratos que cambian despues de ser consumidos).
 *
 * <p>Fija lo que <b>este</b> servicio necesita de ms-finanzas, ni mas ni menos:
 * los nombres de campo exactos de su API —{@code jugadorUid},
 * {@code referenciaId}, {@code saldoDisponible}—, la cabecera
 * {@code Idempotency-Key}, y sobre todo <b>los dos {@code type} URI que separan
 * un rechazo de negocio de una averia</b>. Ese ultimo punto es el que hace util
 * el pacto: si manana ms-finanzas devolviera el saldo insuficiente como un 500
 * generico, aqui no se veria como un cambio de codigo sino como lo que es —un
 * jugador sin creditos viendo "error del servidor" y tumbando el cortacircuitos
 * para todos los demas.
 *
 * <p>Las pruebas corren contra el servidor simulado de Pact, asi que no hace
 * falta levantar ms-finanzas. El pacto queda en {@code contracts/pactos/} para
 * que su dueno lo verifique contra su implementacion cuando quiera.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "ms-finanzas", pactVersion = PactSpecVersion.V3)
class CreditosPactoTest {

    private static final String CONSUMIDOR = "ms-subastas";
    private static final String BASE_TYPE = "https://nexusbattles.upb.edu.co/errors/";

    private static final UUID JUGADOR = UUID.fromString("77777777-0000-0000-0000-0000000000cc");
    private static final UUID VENDEDOR = UUID.fromString("88888888-0000-0000-0000-0000000000dd");
    private static final UUID SUBASTA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID RESERVA = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private CreditoClientHttp clienteContra(MockServer servidor) {
        return new CreditoClientHttp(URI.create(servidor.getUrl() + "/api/v1"),
                HttpClient.newHttpClient(), new ObjectMapper(), Duration.ofSeconds(5));
    }

    // --- reservar creditos -------------------------------------------------

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact reservaConSaldo(PactDslWithProvider constructor) {
        return constructor
                .given("el jugador tiene saldo disponible suficiente")
                .uponReceiving("una reserva de creditos para una puja")
                .path("/api/v1/creditos/reservar")
                .method("POST")
                .matchHeader("Idempotency-Key", ".+", "clave-de-ejemplo-0001")
                // El cliente manda 'application/json' a secas. Pact supondria
                // '; charset=UTF-8' si no se dice, y el pacto quedaria fijando
                // una cabecera que nadie envia.
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .stringType("jugadorUid", JUGADOR.toString())
                        .numberType("monto", 110)
                        .stringType("concepto")
                        .stringType("referenciaId", SUBASTA.toString()))
                .willRespondWith()
                .status(201)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .uuid("reservaId", RESERVA)
                        .stringType("jugadorUid", JUGADOR.toString())
                        .decimalType("monto", 110.0)
                        .stringType("estado", "ACTIVA"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "reservaConSaldo")
    void reservarDevuelveElIdentificadorDeLaReserva(MockServer servidor) {
        ReservaCredito reserva = clienteContra(servidor)
                .reservar(JUGADOR, new BigDecimal("110"), SUBASTA, "clave-de-la-puja");

        assertEquals(RESERVA, reserva.id());
        assertEquals(JUGADOR, reserva.jugadorId());
    }

    // --- el rechazo de negocio que NO es una averia -------------------------

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact reservaSinSaldo(PactDslWithProvider constructor) {
        return constructor
                .given("el jugador no tiene saldo disponible suficiente")
                .uponReceiving("una reserva de creditos que excede el saldo")
                .path("/api/v1/creditos/reservar")
                .method("POST")
                .matchHeader("Idempotency-Key", ".+", "clave-de-ejemplo-0001")
                // El cliente manda 'application/json' a secas. Pact supondria
                // '; charset=UTF-8' si no se dice, y el pacto quedaria fijando
                // una cabecera que nadie envia.
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .stringType("jugadorUid", JUGADOR.toString())
                        .numberType("monto", 999999)
                        .stringType("concepto")
                        .stringType("referenciaId", SUBASTA.toString()))
                .willRespondWith()
                .status(422)
                .headers(Map.of("Content-Type", "application/problem+json"))
                .body(new PactDslJsonBody()
                        .stringValue("type", BASE_TYPE + "saldo-insuficiente")
                        .integerType("status", 422)
                        .stringType("detail"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "reservaSinSaldo")
    void elSaldoInsuficienteLlegaComoRechazoDeNegocioYNoComoAveria(MockServer servidor) {
        CreditoClientException error = assertThrows(CreditoClientException.class,
                () -> clienteContra(servidor)
                        .reservar(JUGADOR, new BigDecimal("999999"), SUBASTA, "clave-sin-saldo"));

        // Esto es lo que el pacto protege: si esta respuesta dejara de traer su
        // type, el motivo caeria en RESPUESTA_INESPERADA o en avería, y con la
        // avería vendrian el reintento y el cortacircuitos abierto.
        assertEquals(CreditoClientException.Motivo.SALDO_INSUFICIENTE, error.getMotivo());
    }

    // --- consumir al cerrar la subasta -------------------------------------

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact consumoDeReservaActiva(PactDslWithProvider constructor) {
        return constructor
                .given("existe una reserva activa del comprador")
                .uponReceiving("el consumo de la reserva al adjudicar la subasta")
                .path("/api/v1/creditos/reservas/" + RESERVA + "/consumir")
                .method("POST")
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody().stringType("vendedorUid", VENDEDOR.toString()))
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .uuid("reservaId", RESERVA)
                        .stringType("estado", "CONSUMIDA")
                        .decimalType("montoDebitado", 110.0)
                        .stringType("vendedorUid", VENDEDOR.toString())
                        .stringType("transaccionId"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "consumoDeReservaActiva")
    void consumirLlevaAlVendedorParaQueAlguienCobre(MockServer servidor) {
        // El vendedor es obligatorio en el cuerpo: sin el, ms-finanzas cobra al
        // comprador y no abona a nadie.
        assertDoesNotThrow(() -> clienteContra(servidor).consumir(RESERVA, VENDEDOR));
    }

    // --- liberar una reserva que ya no existe -------------------------------

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact liberacionDeReservaInexistente(PactDslWithProvider constructor) {
        return constructor
                .given("no existe ninguna reserva con ese identificador")
                .uponReceiving("la liberacion de una reserva desconocida")
                .path("/api/v1/creditos/reservas/" + RESERVA + "/liberar")
                .method("POST")
                .willRespondWith()
                .status(404)
                .headers(Map.of("Content-Type", "application/problem+json"))
                .body(new PactDslJsonBody()
                        .stringValue("type", BASE_TYPE + "reserva-no-encontrada")
                        .integerType("status", 404)
                        .stringType("detail"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "liberacionDeReservaInexistente")
    void liberarUnaReservaQueYaNoExisteNoRompeElCierre(MockServer servidor) {
        // El cierre por vencimiento corre en transaccion: si esto lanzara, el
        // cierre revertiria y el job reintentaria la misma subasta cada 30 s
        // para siempre. El type es lo que permite distinguirlo de una ruta mal
        // escrita, que si debe fallar.
        assertDoesNotThrow(() -> clienteContra(servidor).liberar(RESERVA));
    }

    // --- saldo --------------------------------------------------------------

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact consultaDeSaldo(PactDslWithProvider constructor) {
        return constructor
                .given("el jugador tiene una cuenta de creditos")
                .uponReceiving("la consulta del saldo del jugador")
                .path("/api/v1/creditos/" + JUGADOR + "/saldo")
                .method("GET")
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .stringType("jugadorUid", JUGADOR.toString())
                        .decimalType("saldoBruto", 500.0)
                        .decimalType("saldoReservado", 200.0)
                        .decimalType("saldoDisponible", 300.0))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "consultaDeSaldo")
    void elSaldoQueSeUsaEsElDisponibleNoElBruto(MockServer servidor) {
        BigDecimal saldo = clienteContra(servidor).saldoDisponible(JUGADOR);

        // 300 y no 500: el bruto no descuenta lo ya reservado y permitiria
        // comprometer dos veces los mismos creditos en pujas simultaneas.
        assertEquals(0, new BigDecimal("300.0").compareTo(saldo));
    }
}
