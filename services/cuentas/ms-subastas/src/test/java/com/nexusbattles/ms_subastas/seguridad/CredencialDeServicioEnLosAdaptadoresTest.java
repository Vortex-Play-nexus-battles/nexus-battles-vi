package com.nexusbattles.ms_subastas.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.comun.seguridad.servicio.CredencialDeServicioNoDisponible;
import com.nexusbattles.comun.seguridad.servicio.TokenDeServicio;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientHttp;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoNoDisponibleException;
import com.nexusbattles.ms_subastas.subastas.port.FinanzasPublicacionClientHttp;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientHttp;
import com.sun.net.httpserver.HttpServer;

/**
 * ADR-005 / #455: cada peticion de ms-subastas a ms-finanzas y a inventario
 * lleva la credencial de <b>este servicio</b> en {@code Authorization}, y el
 * jugador afectado sigue viajando en el cuerpo. Contra un servidor HTTP real
 * del JDK, que es donde se ve la cabecera que sale por el cable.
 */
class CredencialDeServicioEnLosAdaptadoresTest {

    private static final UUID JUGADOR = UUID.fromString("77777777-0000-0000-0000-0000000000cc");
    private static final UUID SUBASTA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final String TOKEN = "eyJ.token-de-servicio-de-subastas.firma";

    private HttpServer servidor;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> cuerpo = new AtomicReference<>();
    private final ObjectMapper mapper = new ObjectMapper();

    private URI base() {
        return URI.create("http://localhost:" + servidor.getAddress().getPort() + "/api/v1");
    }

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/", intercambio -> {
            authorization.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String ruta = intercambio.getRequestURI().getPath();
            String respuesta = ruta.endsWith("/reservar")
                    ? "{\"reservaId\":\"bbbbbbbb-0000-0000-0000-000000000002\",\"jugadorUid\":\"" + JUGADOR
                            + "\",\"monto\":10,\"estado\":\"RESERVADA\"}"
                    : ruta.endsWith("/saldo")
                            ? "{\"jugadorUid\":\"" + JUGADOR + "\",\"saldoBruto\":50,\"saldoReservado\":0,\"saldoDisponible\":50}"
                            : ruta.endsWith("/debitar")
                                    ? "{\"refId\":\"sub-publicacion-" + SUBASTA + "\",\"transaccionId\":\"tx\",\"estado\":\"DEBITADO\","
                                            + "\"montoDebitado\":1,\"nuevoSaldoDisponible\":49}"
                                    : "{}";
            byte[] salida = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(ruta.endsWith("/reservar") ? 201 : 200, salida.length);
            intercambio.getResponseBody().write(salida);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void bajar() {
        servidor.stop(0);
    }

    @Test
    @DisplayName("el cliente de creditos de pujas manda la credencial y el jugador va en el cuerpo")
    void creditosDePujas() {
        CreditoClientHttp cliente = new CreditoClientHttp(base(), HttpClient.newHttpClient(), mapper,
                Duration.ofSeconds(2), PortadorDeServicio.de(() -> TOKEN));

        cliente.reservar(JUGADOR, BigDecimal.TEN, SUBASTA, "clave-1");

        assertEquals("Bearer " + TOKEN, authorization.get());
        assertTrue(cuerpo.get().contains("\"jugadorUid\":\"" + JUGADOR + "\""),
                "el jugador afectado viaja en el cuerpo, no en la credencial");

        cliente.saldoDisponible(JUGADOR);
        assertEquals("Bearer " + TOKEN, authorization.get());
    }

    @Test
    @DisplayName("el adaptador de comisiones de publicacion tambien la manda")
    void comisionesDePublicacion() {
        FinanzasPublicacionClientHttp cliente = new FinanzasPublicacionClientHttp(base(), HttpClient.newHttpClient(),
                mapper, Duration.ofSeconds(2), PortadorDeServicio.de(() -> TOKEN));

        cliente.debitarComision(JUGADOR, BigDecimal.ONE, SUBASTA, "comision-publicacion-24h");

        assertEquals("Bearer " + TOKEN, authorization.get());
        assertTrue(cuerpo.get().contains("\"uid\":\"" + JUGADOR + "\""));
    }

    @Test
    @DisplayName("el cliente de inventario la manda en el bloqueo por subasta")
    void inventario() {
        InventarioClientHttp cliente = new InventarioClientHttp(
                URI.create("http://localhost:" + servidor.getAddress().getPort()), HttpClient.newHttpClient(),
                mapper, Duration.ofSeconds(2), PortadorDeServicio.de(() -> TOKEN));

        cliente.reservar("elemento-1", JUGADOR, SUBASTA, "clave-2");

        assertEquals("Bearer " + TOKEN, authorization.get());
        assertTrue(cuerpo.get().contains(JUGADOR.toString()));
    }

    @Test
    @DisplayName("sin credencial configurada no se inventa ninguna: la peticion sale sin Authorization")
    void sinCredencial() {
        CreditoClientHttp cliente = new CreditoClientHttp(base(), HttpClient.newHttpClient(), mapper,
                Duration.ofSeconds(2), PortadorDeServicio.ninguno());

        cliente.saldoDisponible(JUGADOR);

        assertNull(authorization.get());
        assertFalse(PortadorDeServicio.ninguno().presente());
    }

    @Test
    @DisplayName("si el emisor no entrega la credencial, la llamada no sale y se trata como finanzas caido")
    void emisorCaido() {
        TokenDeServicio sinEmisor = () -> {
            throw new CredencialDeServicioNoDisponible("ms-identidad no responde", new IOException("connection refused"));
        };
        CreditoClientHttp cliente = new CreditoClientHttp(base(), HttpClient.newHttpClient(), mapper,
                Duration.ofSeconds(2), PortadorDeServicio.de(sinEmisor));

        assertThrows(CreditoNoDisponibleException.class, () -> cliente.saldoDisponible(JUGADOR));
        assertNull(authorization.get(), "ninguna peticion llego al servidor");
    }

    @Test
    @DisplayName("en modo http, arrancar sin credencial es un error de configuracion, no una sorpresa en la primera puja")
    void modoHttpExigeCredencial() {
        @SuppressWarnings("unchecked")
        ObjectProvider<TokenDeServicio> vacio = mock(ObjectProvider.class);
        when(vacio.getIfAvailable()).thenReturn(null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> CredencialSaliente.obligatoria(vacio, "ms-finanzas"));
        assertTrue(error.getMessage().contains("DIRECTORIO_ACTIVO_CLIENT_ID"));

        @SuppressWarnings("unchecked")
        ObjectProvider<TokenDeServicio> conToken = mock(ObjectProvider.class);
        when(conToken.getIfAvailable()).thenReturn(() -> TOKEN);
        assertTrue(CredencialSaliente.obligatoria(conToken, "ms-finanzas").presente());
    }
}
