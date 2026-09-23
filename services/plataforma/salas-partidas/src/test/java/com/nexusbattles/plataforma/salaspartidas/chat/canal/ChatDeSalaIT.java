package com.nexusbattles.plataforma.salaspartidas.chat.canal;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexusbattles.plataforma.salaspartidas.chat.Canal;
import com.nexusbattles.plataforma.salaspartidas.chat.HistorialDeChat;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDelHeroe;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chat de sala extremo a extremo — HU-JUE-015 (RF-JUE-015).
 *
 * <p>Lo que faltaba segun #441: una prueba del chat por su canal real. Aqui
 * hay servidor STOMP real, PostgreSQL real (el historial se guarda) y los
 * <b>clientes HTTP reales</b> de la lista negra (HU-ADM-002) y de las
 * sanciones (RF-USR-004) hablando con un doble HTTP que responde exactamente
 * las formas de {@code moderacion-lista-negra.yaml} y
 * {@code moderacion-sanciones-consulta.yaml}. Ningun puerto de dominio se
 * sustituye por un mock: lo unico fingido es el modulo ajeno, y por HTTP.
 *
 * <p>Se afirma comportamiento visible (CA-01, CA-03): que el mensaje llegue a
 * los suscritos con el payload del contrato y quede en el historial; que el
 * silenciado no publique y reciba el motivo por su cola privada; que con
 * sanciones caido nadie se cuele; que la lista negra bloquee.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ChatDeSalaIT.SeguridadDePrueba.class)
@DisplayName("Chat de sala extremo a extremo (HU-JUE-015)")
class ChatDeSalaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String ANFITRION = "anfitrion";
    private static final String SILENCIADO = "silenciado";
    private static final UUID ID_ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ID_SILENCIADO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** Doble HTTP de moderacion-sanciones: lista negra + consulta de sancion. */
    private static HttpServer moderacion;
    private static final AtomicBoolean SANCIONES_CAIDAS = new AtomicBoolean(false);

    @BeforeAll
    static void levantarModeracionDePrueba() throws Exception {
        moderacion = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        moderacion.createContext("/api/v1/lista-negra/verificar", intercambio -> {
            String texto = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean aprobado = !texto.contains("prohibida");
            responder(intercambio, 200, "{\"aprobado\":" + aprobado + (aprobado ? "" : ",\"motivo\":\"termino prohibido\"") + "}");
        });
        moderacion.createContext("/api/v1/sanciones/usuarios/", intercambio -> {
            if (SANCIONES_CAIDAS.get()) {
                responder(intercambio, 500, "{}");
                return;
            }
            String ruta = intercambio.getRequestURI().getPath();
            boolean silenciado = ruta.contains(ID_SILENCIADO.toString());
            responder(intercambio, 200, silenciado
                    ? "{\"sancionActiva\":true,\"motivo\":\"Lenguaje ofensivo reiterado\",\"vigenteHasta\":\"2026-12-01T00:00:00Z\"}"
                    : "{\"sancionActiva\":false,\"motivo\":null,\"vigenteHasta\":null}");
        });
        moderacion.start();
    }

    private static void responder(com.sun.net.httpserver.HttpExchange intercambio, int estado, String json)
            throws java.io.IOException {
        byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, cuerpo.length);
        try (var salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }

    @AfterAll
    static void apagarModeracionDePrueba() {
        if (moderacion != null) {
            moderacion.stop(0);
        }
    }

    @DynamicPropertySource
    static void apuntarAlDoble(DynamicPropertyRegistry registro) {
        registro.add("chat.lista-negra.url",
                () -> "http://127.0.0.1:" + moderacion.getAddress().getPort() + "/api/v1/lista-negra/verificar");
        registro.add("salas.sanciones.url",
                () -> "http://127.0.0.1:" + moderacion.getAddress().getPort() + "/api/v1");
    }

    @Value("${local.server.port}")
    private int puerto;

    @Autowired
    private SimpMessagingTemplate mensajeria;

    @Autowired
    private HistorialDeChat historial;

    /** Crear la sala pasa por la puerta de heroe; esa integracion se prueba aparte. */
    @MockitoBean
    private com.nexusbattles.plataforma.salaspartidas.aplicacion.HeroeDelJugador heroes;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void elInventarioDejaPasar() {
        SANCIONES_CAIDAS.set(false);
        Mockito.when(heroes.consultar(ArgumentMatchers.any()))
                .thenReturn(EstadoDelHeroe.disponible(new HeroeDeCombate("h-1", "Sombra de Vael", null, 7, 140, 140)));
    }

    @TestConfiguration
    static class SeguridadDePrueba {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(token)
                    .claim("uid", ANFITRION.equals(token) ? ID_ANFITRION.toString() : ID_SILENCIADO.toString())
                    .claim("preferred_username", token)
                    .claim("realm_access", Map.of("roles", List.of("JUGADOR")))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
        }
    }

    private UUID crearSala() throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/salas"))
                .header("Authorization", "Bearer " + ANFITRION)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"maximoParticipantes": 4, "modalidad": "HASTA_SEIS", "recompensaCreditos": 0}
                        """))
                .build();
        HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, respuesta.statusCode(), respuesta.body());
        String ubicacion = respuesta.headers().firstValue("Location").orElseThrow();
        return UUID.fromString(ubicacion.substring(ubicacion.lastIndexOf('/') + 1));
    }

    private StompSession conectar(String token) throws Exception {
        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer " + token);
        return new WebSocketStompClient(new StandardWebSocketClient())
                .connectAsync("ws://localhost:" + puerto + "/ws", new WebSocketHttpHeaders(), connect,
                        new StompSessionHandlerAdapter() { })
                .get(10, TimeUnit.SECONDS);
    }

    private static final String SONDA = "sonda-de-suscripcion";

    /** Suscripcion confirmada con una sonda, como en CanalDeSalaIT: nada de esperas a ojo. */
    private BlockingQueue<String> suscribirse(StompSession sesion, String destino, boolean conSonda)
            throws Exception {
        BlockingQueue<String> recibidos = new LinkedBlockingQueue<>();
        BlockingQueue<String> sondas = new LinkedBlockingQueue<>();
        sesion.subscribe(destino, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders cabeceras) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders cabeceras, Object cuerpo) {
                String texto = new String((byte[]) cuerpo, StandardCharsets.UTF_8);
                (texto.contains(SONDA) ? sondas : recibidos).add(texto);
            }
        });
        if (conSonda) {
            boolean viva = false;
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!viva && System.nanoTime() < limite) {
                mensajeria.convertAndSend(destino, SONDA);
                viva = sondas.poll(200, TimeUnit.MILLISECONDS) != null;
            }
            assertTrue(viva, "la suscripcion a " + destino + " nunca quedo activa");
            while (sondas.poll(200, TimeUnit.MILLISECONDS) != null) {
                // vaciando
            }
        } else {
            // La cola privada no admite sonda desde fuera; un respiro basta
            // porque el SUBSCRIBE viaja antes que el SEND por la misma sesion.
            Thread.sleep(300);
        }
        return recibidos;
    }

    private static void enviar(StompSession sesion, UUID idSala, String texto) {
        sesion.send("/app/salas/" + idSala + "/chat",
                ("{\"texto\":\"" + texto + "\"}").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("CA-01: el mensaje llega a los suscritos con el payload del contrato y queda en el historial")
    void elMensajeLlegaYSeGuarda() throws Exception {
        UUID idSala = crearSala();
        StompSession anfitrion = conectar(ANFITRION);
        BlockingQueue<String> canal = suscribirse(anfitrion, Canal.deSala(idSala).destino(), true);

        enviar(anfitrion, idSala, "vamos con todo");

        String mensaje = canal.poll(10, TimeUnit.SECONDS);
        assertNotNull(mensaje, "el mensaje no salio por el canal de la sala");
        assertAll(
                () -> assertTrue(mensaje.contains("\"tipo\":\"chat.mensaje\""), mensaje),
                () -> assertTrue(mensaje.contains("\"texto\":\"vamos con todo\""), mensaje),
                () -> assertTrue(mensaje.contains("\"id\":\"" + ID_ANFITRION + "\""), mensaje),
                () -> assertTrue(mensaje.contains("\"apodo\":\"anfitrion\""), mensaje),
                () -> assertEquals(1, historial.ultimos(Canal.deSala(idSala), 50).size(), "queda en el historial"));
    }

    @Test
    @DisplayName("CA-03: el silenciado por sancion no publica nada y recibe el motivo por su cola privada")
    void elSilenciadoNoPublica() throws Exception {
        UUID idSala = crearSala();
        StompSession anfitrion = conectar(ANFITRION);
        BlockingQueue<String> canal = suscribirse(anfitrion, Canal.deSala(idSala).destino(), true);
        StompSession silenciado = conectar(SILENCIADO);
        BlockingQueue<String> colaPrivada = suscribirse(silenciado, "/usuario/cola/salas", false);

        enviar(silenciado, idSala, "hola?");

        String rechazo = colaPrivada.poll(10, TimeUnit.SECONDS);
        assertNotNull(rechazo, "el rechazo tiene que volver por la cola privada");
        assertAll(
                () -> assertTrue(rechazo.contains("jugador-silenciado"), rechazo),
                () -> assertTrue(rechazo.contains("\"status\":403"), rechazo),
                () -> assertNull(canal.poll(2, TimeUnit.SECONDS), "nada debe salir al canal"),
                () -> assertEquals(0, historial.ultimos(Canal.deSala(idSala), 50).size()));
    }

    @Test
    @DisplayName("CA-03: con sanciones caido nadie se cuela: 503 por la cola privada y nada en el canal")
    void conSancionesCaidoNadieSeCuela() throws Exception {
        UUID idSala = crearSala();
        StompSession anfitrion = conectar(ANFITRION);
        BlockingQueue<String> canal = suscribirse(anfitrion, Canal.deSala(idSala).destino(), true);
        BlockingQueue<String> colaPrivada = suscribirse(anfitrion, "/usuario/cola/salas", false);
        SANCIONES_CAIDAS.set(true);

        enviar(anfitrion, idSala, "sigo aqui?");

        String rechazo = colaPrivada.poll(10, TimeUnit.SECONDS);
        assertNotNull(rechazo);
        assertAll(
                () -> assertTrue(rechazo.contains("sanciones-no-disponibles"), rechazo),
                () -> assertTrue(rechazo.contains("\"status\":503"), rechazo),
                () -> assertNull(canal.poll(2, TimeUnit.SECONDS), "sin comprobar la sancion no se publica"));
    }

    @Test
    @DisplayName("CA-03: la lista negra bloquea el contenido y lo dice por la cola privada")
    void laListaNegraBloquea() throws Exception {
        UUID idSala = crearSala();
        StompSession anfitrion = conectar(ANFITRION);
        BlockingQueue<String> canal = suscribirse(anfitrion, Canal.deSala(idSala).destino(), true);
        BlockingQueue<String> colaPrivada = suscribirse(anfitrion, "/usuario/cola/salas", false);

        enviar(anfitrion, idSala, "palabra prohibida");

        String rechazo = colaPrivada.poll(10, TimeUnit.SECONDS);
        assertNotNull(rechazo);
        assertAll(
                () -> assertTrue(rechazo.contains("contenido-bloqueado"), rechazo),
                () -> assertNull(canal.poll(2, TimeUnit.SECONDS)));
    }
}
