package com.nexusbattles.ms_subastas.subastas.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClient;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientFake;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El canal de una subasta ({@code /topic/subastas/{subastaId}},
 * {@code contracts/websocket/subastas.yaml} 1.1.0) de extremo a extremo, por
 * STOMP sobre un WebSocket de verdad en {@code /api/v1/ws-subastas}: el mismo
 * endpoint que el borde publica.
 *
 * <p>Se comprueba lo que el encargo de B8 pide: CONNECT con y sin token,
 * SUBSCRIBE (el listado para todos, el canal de una subasta solo con sesion),
 * que una puja real hecha por la API llega por el canal de esa subasta, y que
 * tras reconectar se sigue recibiendo. Tokens con la forma real de ms-identidad
 * (RS256 + JWKS); nada del canal esta simulado.
 *
 * <p>Sin esperas a ojo: la suscripcion se da por viva cuando vuelve una sonda
 * publicada por el mismo destino (el broker simple no garantiza RECEIPT).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.finanzas.modo=prueba",
                "app.pujas.emision-automatica-intervalo-ms=3600000",
                "app.subastas.cierre-intervalo-ms=3600000",
                "app.notificaciones.drenaje-intervalo-ms=3600000",
                // B8: el drenador de correos tambien es un trabajo programado:
                // fuera del camino de la prueba.
                "app.correo.drenaje-intervalo-ms=3600000",
                "app.subastas.recordatorio-intervalo-ms=3600000",
                "app.subastas.pendientes-intervalo-ms=3600000",
                "app.pujas.intervalo-minimo-segundos=0"
        })
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Canal en vivo de una subasta (STOMP, B8)")
class CanalDeSubastaIT {

    private static final String SONDA = "sonda-de-suscripcion";

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @TestConfiguration
    static class CreditosDePrueba {
        @Bean
        CreditoClientFake creditoClientFake() {
            return new CreditoClientFake();
        }
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private SimpMessagingTemplate mensajeria;

    @Autowired
    private SubastaRepository subastas;

    @Autowired
    private CreditoClient creditos;

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    // --- utilidades -------------------------------------------------------------

    private UUID jugadorConSaldo() {
        UUID jugador = UUID.randomUUID();
        ((CreditoClientFake) creditos).acreditar(jugador, new BigDecimal("10000"));
        return jugador;
    }

    private String token(UUID jugador) {
        return emisor.tokenDeJugador("jugador_" + jugador.toString().substring(0, 6), jugador);
    }

    private Subasta subastaActiva() {
        Subasta subasta = new Subasta();
        subasta.setId(UUID.randomUUID());
        subasta.setProductoId(UUID.randomUUID());
        subasta.setElementoInventarioId("elem-" + subasta.getId());
        subasta.setVendedorId(UUID.randomUUID());
        subasta.setPrecioInicial(new BigDecimal("100"));
        subasta.setOfertaVigente(new BigDecimal("100"));
        subasta.setIncrementoMinimo(new BigDecimal("10"));
        subasta.setEstado(EstadoSubasta.ACTIVA);
        subasta.setFechaFin(Instant.now().plus(Duration.ofDays(1)));
        subasta.setNombreProducto("Arco de Luna");
        return subastas.saveAndFlush(subasta);
    }

    private HttpResponse<String> pujar(Subasta subasta, UUID jugador, String monto) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + puerto + "/api/v1/subastas/" + subasta.getId() + "/pujas"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token(jugador))
                .header("Idempotency-Key", "k-" + UUID.randomUUID())
                .POST(HttpRequest.BodyPublishers.ofString("{\"monto\":\"" + monto + "\"}"))
                .build();
        return http.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    /** Conecta como el navegador: handshake sin cabeceras y el Bearer (si lo hay) en el frame CONNECT. */
    private StompSession conectar(String token, StompSessionHandlerAdapter manejador) throws Exception {
        StompHeaders connect = new StompHeaders();
        if (token != null) {
            connect.add("Authorization", "Bearer " + token);
        }
        return new WebSocketStompClient(new StandardWebSocketClient())
                .connectAsync("ws://localhost:" + puerto + "/api/v1/ws-subastas", new WebSocketHttpHeaders(),
                        connect, manejador)
                .get(10, TimeUnit.SECONDS);
    }

    private StompSession conectar(String token) throws Exception {
        return conectar(token, new StompSessionHandlerAdapter() { });
    }

    /** Los ERROR y fallos de transporte de una sesion. */
    private static final class Errores extends StompSessionHandlerAdapter {
        final BlockingQueue<String> recibidos = new LinkedBlockingQueue<>();

        @Override
        public void handleFrame(StompHeaders cabeceras, Object cuerpo) {
            recibidos.add("ERROR: " + cabeceras.getFirst("message"));
        }

        @Override
        public void handleException(StompSession sesion, StompCommand comando, StompHeaders cabeceras, byte[] cuerpo,
                                    Throwable ex) {
            recibidos.add("EXCEPCION: " + ex.getMessage());
        }

        @Override
        public void handleTransportError(StompSession sesion, Throwable ex) {
            recibidos.add("TRANSPORTE: " + ex.getMessage());
        }
    }

    /** Se suscribe y no vuelve hasta que una sonda demuestra que la suscripcion esta viva. */
    private BlockingQueue<String> suscribirse(StompSession sesion, String destino) throws Exception {
        BlockingQueue<String> mensajes = new LinkedBlockingQueue<>();
        BlockingQueue<String> sondas = new LinkedBlockingQueue<>();
        sesion.subscribe(destino, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders cabeceras) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders cabeceras, Object cuerpo) {
                String texto = new String((byte[]) cuerpo, StandardCharsets.UTF_8);
                (texto.contains(SONDA) ? sondas : mensajes).add(texto);
            }
        });
        boolean viva = false;
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!viva && System.nanoTime() < limite) {
            mensajeria.convertAndSend(destino, SONDA);
            viva = sondas.poll(200, TimeUnit.MILLISECONDS) != null;
        }
        assertTrue(viva, "la suscripcion a " + destino + " nunca quedo activa");
        while (sondas.poll(200, TimeUnit.MILLISECONDS) != null) {
            // vaciando las sondas en vuelo
        }
        return mensajes;
    }

    /** El siguiente mensaje de esa subasta, saltando los de otras (el listado es compartido). */
    private JsonNode siguienteDe(BlockingQueue<String> cola, Subasta subasta) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            String texto = cola.poll(500, TimeUnit.MILLISECONDS);
            if (texto != null) {
                JsonNode mensaje = mapper.readTree(texto);
                if (subasta.getId().toString().equals(mensaje.path("id").asText())) {
                    return mensaje;
                }
            }
        }
        fail("no llego ningun mensaje de la subasta " + subasta.getId());
        return null;
    }

    private static void assertMonto(String esperado, JsonNode nodo) {
        assertEquals(0, new BigDecimal(esperado).compareTo(new BigDecimal(nodo.asText())),
                "esperaba " + esperado + " y llego " + nodo);
    }

    // --- CONNECT --------------------------------------------------------------------

    @Test
    @DisplayName("sin token hay sesion de visitante: el listado es publico")
    void visitanteSigueElListado() throws Exception {
        StompSession sesion = conectar(null);
        Subasta subasta = subastaActiva();
        BlockingQueue<String> listado = suscribirse(sesion, SubastaRealtimePublisher.CANAL_LISTADO);

        assertEquals(201, pujar(subasta, jugadorConSaldo(), "100").statusCode());

        JsonNode mensaje = siguienteDe(listado, subasta);
        assertMonto("100", mensaje.path("ofertaVigente"));
        assertEquals(1, mensaje.path("cantidadPujas").asInt());
        sesion.disconnect();
    }

    @Test
    @DisplayName("un token roto no es un visitante: el CONNECT se rechaza")
    void tokenInvalidoNoConecta() {
        assertThrows(Exception.class, () -> conectar("esto-no-es-un-jwt"));
        assertThrows(Exception.class, () -> conectar(emisor.tokenCaducado("lyra", UUID.randomUUID())));
        assertThrows(Exception.class, () -> conectar(emisor.tokenFirmadoPorOtro("lyra", UUID.randomUUID())));
    }

    // --- SUBSCRIBE ------------------------------------------------------------------

    @Test
    @DisplayName("un visitante no puede suscribirse al canal de una subasta")
    void visitanteNoSigueUnaSubasta() throws Exception {
        Errores errores = new Errores();
        StompSession sesion = conectar(null, errores);
        Subasta subasta = subastaActiva();

        sesion.subscribe(SubastaRealtimePublisher.canalDe(subasta.getId()), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders cabeceras) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders cabeceras, Object cuerpo) {
                errores.recibidos.add("MENSAJE INESPERADO");
            }
        });

        String rechazo = errores.recibidos.poll(10, TimeUnit.SECONDS);
        assertNotNull(rechazo, "el servidor tiene que rechazar la suscripcion, no ignorarla");
        assertFalse(rechazo.contains("MENSAJE INESPERADO"), rechazo);
    }

    @Test
    @DisplayName("con sesion, una puja hecha por la API llega por el canal de esa subasta con el estado nuevo")
    void unaPujaLlegaPorElCanalDeLaSubasta() throws Exception {
        UUID jugador = jugadorConSaldo();
        Subasta subasta = subastaActiva();
        StompSession sesion = conectar(token(jugador));
        BlockingQueue<String> canal = suscribirse(sesion, SubastaRealtimePublisher.canalDe(subasta.getId()));

        assertEquals(201, pujar(subasta, jugadorConSaldo(), "100").statusCode());
        JsonNode primera = siguienteDe(canal, subasta);
        assertMonto("100", primera.path("ofertaVigente"));
        assertEquals(1, primera.path("cantidadPujas").asInt());
        assertEquals("ACTIVA", primera.path("estado").asText());

        assertEquals(201, pujar(subasta, jugador, "110").statusCode());
        JsonNode segunda = siguienteDe(canal, subasta);
        assertMonto("110", segunda.path("ofertaVigente"));
        assertEquals(2, segunda.path("cantidadPujas").asInt());
        sesion.disconnect();
    }

    @Test
    @DisplayName("una puja rechazada no publica nada por el canal")
    void unaPujaRechazadaNoSePublica() throws Exception {
        Subasta subasta = subastaActiva();
        StompSession sesion = conectar(token(jugadorConSaldo()));
        BlockingQueue<String> canal = suscribirse(sesion, SubastaRealtimePublisher.canalDe(subasta.getId()));

        assertEquals(409, pujar(subasta, jugadorConSaldo(), "99").statusCode());

        assertNull(canal.poll(2, TimeUnit.SECONDS), "un rechazo no cambia la subasta: no se anuncia");
        sesion.disconnect();
    }

    @Test
    @DisplayName("el canal no acepta mensajes del cliente")
    void noSeAceptaSend() throws Exception {
        Errores errores = new Errores();
        StompSession sesion = conectar(token(jugadorConSaldo()), errores);

        sesion.send("/app/pujar", "{\"monto\":\"1000\"}".getBytes(StandardCharsets.UTF_8));

        assertNotNull(errores.recibidos.poll(10, TimeUnit.SECONDS), "un SEND se contesta con ERROR");
    }

    // --- reconexion -------------------------------------------------------------------

    @Test
    @DisplayName("tras perder la conexion y reconectar, se vuelve a recibir la subasta")
    void reconexion() throws Exception {
        UUID jugador = jugadorConSaldo();
        Subasta subasta = subastaActiva();
        String destino = SubastaRealtimePublisher.canalDe(subasta.getId());

        StompSession primera = conectar(token(jugador));
        BlockingQueue<String> antes = suscribirse(primera, destino);
        assertEquals(201, pujar(subasta, jugadorConSaldo(), "100").statusCode());
        assertMonto("100", siguienteDe(antes, subasta).path("ofertaVigente"));
        primera.disconnect();
        assertFalse(primera.isConnected());

        // Mientras estaba desconectado hubo otra puja: al volver, el cliente
        // relee la ficha (GET /subastas/{id}) y sigue por el canal.
        assertEquals(201, pujar(subasta, jugadorConSaldo(), "110").statusCode());
        HttpResponse<String> ficha = http.send(HttpRequest.newBuilder(URI.create(
                "http://localhost:" + puerto + "/api/v1/subastas/" + subasta.getId())).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertMonto("110", mapper.readTree(ficha.body()).path("ofertaVigente"));

        StompSession segunda = conectar(token(jugador));
        BlockingQueue<String> despues = suscribirse(segunda, destino);
        assertEquals(201, pujar(subasta, jugadorConSaldo(), "120").statusCode());
        JsonNode mensaje = siguienteDe(despues, subasta);
        assertMonto("120", mensaje.path("ofertaVigente"));
        assertEquals(3, mensaje.path("cantidadPujas").asInt());
        segunda.disconnect();
    }
}
