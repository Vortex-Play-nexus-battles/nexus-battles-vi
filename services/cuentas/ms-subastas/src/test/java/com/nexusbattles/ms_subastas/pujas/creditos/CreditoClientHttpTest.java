package com.nexusbattles.ms_subastas.pujas.creditos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contra un servidor HTTP real del JDK, no un mock del cliente: lo que hay que
 * verificar es la peticion que sale por el cable frente a la API que publica
 * ms-finanzas —ruta, verbo, cabeceras y nombres de campo— y eso un mock no lo
 * comprueba. Los nombres importan especialmente: ese servicio usa jugadorUid y
 * referenciaId, que no son los de nuestro dominio.
 */
class CreditoClientHttpTest {

    private static final UUID JUGADOR = UUID.fromString("77777777-0000-0000-0000-0000000000cc");
    private static final UUID VENDEDOR = UUID.fromString("88888888-0000-0000-0000-0000000000dd");
    private static final UUID SUBASTA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID RESERVA = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private HttpServer servidor;
    private CreditoClientHttp cliente;
    private final AtomicReference<String> metodo = new AtomicReference<>();
    private final AtomicReference<String> ruta = new AtomicReference<>();
    private final AtomicReference<String> clave = new AtomicReference<>();
    private final AtomicReference<String> cuerpo = new AtomicReference<>();
    private final AtomicInteger codigo = new AtomicInteger(200);
    private final AtomicReference<String> respuesta = new AtomicReference<>("{}");

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/api/v1/creditos", intercambio -> {
            metodo.set(intercambio.getRequestMethod());
            ruta.set(intercambio.getRequestURI().getPath());
            clave.set(intercambio.getRequestHeaders().getFirst("Idempotency-Key"));
            cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] salida = respuesta.get().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(codigo.get(), salida.length);
            intercambio.getResponseBody().write(salida);
            intercambio.close();
        });
        servidor.start();
        cliente = new CreditoClientHttp(
                URI.create("http://localhost:" + servidor.getAddress().getPort() + "/api/v1"),
                HttpClient.newHttpClient(), new ObjectMapper(), Duration.ofSeconds(2));
    }

    @AfterEach
    void bajar() {
        servidor.stop(0);
    }

    // --- reservar ----------------------------------------------------------

    @Test
    void reservarUsaLosNombresDeCampoDeMsFinanzas() throws Exception {
        codigo.set(201);
        respuesta.set("{\"reservaId\":\"" + RESERVA + "\",\"jugadorUid\":\"" + JUGADOR
                + "\",\"monto\":110.00,\"estado\":\"RESERVADA\"}");

        ReservaCredito reserva = cliente.reservar(JUGADOR, new BigDecimal("110.00"), SUBASTA, "clave-larga-1");

        assertEquals("POST", metodo.get());
        assertEquals("/api/v1/creditos/reservar", ruta.get());
        assertEquals("clave-larga-1", clave.get());

        JsonNode json = new ObjectMapper().readTree(cuerpo.get());
        // Sus nombres, no los nuestros: traducirlos aqui es lo que evita que su
        // nomenclatura se filtre al motor de pujas.
        assertEquals(JUGADOR.toString(), json.get("jugadorUid").asText());
        assertEquals(SUBASTA.toString(), json.get("referenciaId").asText());
        assertEquals(0, new BigDecimal("110.00").compareTo(json.get("monto").decimalValue()));

        assertEquals(RESERVA, reserva.id());
        assertEquals(JUGADOR, reserva.jugadorId());
    }

    /** ms-finanzas la declara obligatoria: sin ella responde 400. Se corta antes del viaje. */
    @Test
    void reservarSinClaveDeIdempotenciaNiSaleDeCasa() {
        assertThrows(CreditoClientException.class,
                () -> cliente.reservar(JUGADOR, new BigDecimal("110"), SUBASTA, "  "));
        assertEquals(null, metodo.get());
    }

    // --- consumir ----------------------------------------------------------

    @Test
    void consumirMandaElVendedorParaQueAlguienCobre() throws Exception {
        cliente.consumir(RESERVA, VENDEDOR);

        assertEquals("POST", metodo.get());
        assertEquals("/api/v1/creditos/reservas/" + RESERVA + "/consumir", ruta.get());
        assertEquals(VENDEDOR.toString(), new ObjectMapper().readTree(cuerpo.get()).get("vendedorUid").asText());
    }

    /**
     * ms-finanzas admite vendedorUid nulo y entonces cobra al comprador sin
     * abonar a nadie. En una subasta siempre hay a quien pagarle, asi que
     * dejarlo pasar seria quedarse los creditos por el camino.
     */
    @Test
    void consumirSinVendedorSeRechazaAntesDeCobrarle() {
        assertThrows(CreditoClientException.class, () -> cliente.consumir(RESERVA, null));
        assertEquals(null, metodo.get(), "no se puede cobrar al comprador sin saber quien cobra");
    }

    @Test
    void liberarUsaLaRutaConElIdentificadorDeLaReserva() {
        cliente.liberar(RESERVA);

        assertEquals("POST", metodo.get());
        assertEquals("/api/v1/creditos/reservas/" + RESERVA + "/liberar", ruta.get());
    }

    // --- saldo -------------------------------------------------------------

    @Test
    void elSaldoQueSeUsaEsElDisponibleNoElBruto() {
        respuesta.set("{\"jugadorUid\":\"" + JUGADOR
                + "\",\"saldoBruto\":5000.00,\"saldoReservado\":1500.00,\"saldoDisponible\":3500.00}");

        BigDecimal saldo = cliente.saldoDisponible(JUGADOR);

        assertEquals("GET", metodo.get());
        assertEquals("/api/v1/creditos/" + JUGADOR + "/saldo", ruta.get());
        // El bruto permitiria comprometerse dos veces con los mismos creditos
        // en pujas simultaneas.
        assertEquals(0, new BigDecimal("3500.00").compareTo(saldo));
    }

    // --- averia frente a rechazo ------------------------------------------

    /**
     * La distincion que sostiene el cortacircuitos: solo la indisponibilidad se
     * reintenta y solo ella cuenta como fallo.
     */
    @Test
    void un500SeTrataComoAveriaYNoComoRechazoDeNegocio() {
        codigo.set(500);

        CreditoClientException error = assertThrows(CreditoNoDisponibleException.class,
                () -> cliente.liberar(RESERVA));

        assertEquals(CreditoClientException.Motivo.SERVICIO_NO_DISPONIBLE, error.getMotivo());
        // Y el mensaje dice por que no se puede afinar mas: hoy ms-finanzas
        // manda el saldo insuficiente tambien como 500.
        assertTrue(error.getMessage().contains("saldo insuficiente"), error.getMessage());
    }

    @Test
    void un4xxInesperadoNoSeConfundeConUnaAveria() {
        codigo.set(400);

        CreditoClientException error = assertThrows(CreditoClientException.class,
                () -> cliente.liberar(RESERVA));

        assertFalse(error instanceof CreditoNoDisponibleException,
                "un 400 es culpa de la peticion, no del servicio: reintentarlo no lo arregla");
        assertEquals(CreditoClientException.Motivo.RESPUESTA_INESPERADA, error.getMotivo());
    }

    @Test
    void siMsFinanzasNoRespondeSeReportaComoAveria() {
        CreditoClientHttp haciaLaNada = new CreditoClientHttp(
                URI.create("http://localhost:1/api/v1"), HttpClient.newHttpClient(),
                new ObjectMapper(), Duration.ofMillis(300));

        assertThrows(CreditoNoDisponibleException.class, () -> haciaLaNada.saldoDisponible(JUGADOR));
    }

    @Test
    void unaRespuestaIlegibleNoPasaPorBuena() {
        respuesta.set("esto no es json");

        assertThrows(CreditoClientException.class, () -> cliente.saldoDisponible(JUGADOR));
    }
}
