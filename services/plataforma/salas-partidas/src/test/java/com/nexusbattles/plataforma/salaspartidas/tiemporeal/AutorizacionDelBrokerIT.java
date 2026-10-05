package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.chat.Canal;
import com.nexusbattles.plataforma.salaspartidas.chat.HistorialDeChat;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDelHeroe;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
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
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Autorizacion del broker con STOMP de verdad — B6 (auditoria de septiembre).
 *
 * <p>Cada caso es un agujero que el broker simple dejaba abierto y que
 * {@link AutorizacionDeDestinos} cierra: se comprueba desde fuera, con un
 * cliente STOMP que hace lo que haria alguien que ya no usa la interfaz, que el
 * servidor responde {@code ERROR} y que el mensaje no llega a nadie. Servidor
 * con puerto, broker real y PostgreSQL 17 por Testcontainers; los unicos dobles
 * son el decodificador de tokens y las puertas de heroe y de sancion, que no
 * son lo que se prueba.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AutorizacionDelBrokerIT.SeguridadDePrueba.class)
@DisplayName("Autorizacion del broker STOMP (B6)")
class AutorizacionDelBrokerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String ANFITRIONA = "anfitriona";
    private static final String AJENO = "ajeno";
    private static final Map<String, UUID> IDENTIDADES = Map.of(
            ANFITRIONA, UUID.fromString("11111111-1111-1111-1111-111111111111"),
            AJENO, UUID.fromString("33333333-3333-3333-3333-333333333333"));

    private static final String SONDA = "sonda-de-suscripcion";

    @Value("${local.server.port}")
    private int puerto;

    @Autowired
    private SimpMessagingTemplate mensajeria;

    @Autowired
    private HistorialDeChat historial;

    @MockitoBean
    private com.nexusbattles.plataforma.salaspartidas.aplicacion.HeroeDelJugador heroes;

    @MockitoBean
    private com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesDelJugador sanciones;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void lasPuertasDejanPasar() {
        Mockito.when(heroes.consultar(ArgumentMatchers.any()))
                .thenReturn(EstadoDelHeroe.disponible(new HeroeDeCombate("h-1", "Sombra de Vael", null, 7, 140, 140)));
    }

    @TestConfiguration
    static class SeguridadDePrueba {

        /** El token literal es el apodo; el uid sale de la tabla de arriba, como en ms-identidad. */
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(token)
                    .claim("uid", IDENTIDADES.get(token).toString())
                    .claim("rol", "JUGADOR")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
        }
    }

    // ------------------------------------------------------------------ ayudas

    private UUID crearSala(boolean privada) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/salas"))
                .header("Authorization", "Bearer " + ANFITRIONA)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"maximoParticipantes\": 4, \"modalidad\": \"HASTA_SEIS\", \"recompensaCreditos\": 0,"
                                + " \"privada\": " + privada + "}"))
                .build();
        HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, respuesta.statusCode(), respuesta.body());
        String ubicacion = respuesta.headers().firstValue("Location").orElseThrow();
        return UUID.fromString(ubicacion.substring(ubicacion.lastIndexOf('/') + 1));
    }

    /** Recoge los ERROR y los cierres que recibe una sesion. */
    private static final class Registro extends StompSessionHandlerAdapter {
        final BlockingQueue<String> errores = new LinkedBlockingQueue<>();

        @Override
        public void handleFrame(StompHeaders cabeceras, Object cuerpo) {
            errores.add("ERROR: " + cabeceras.getFirst("message"));
        }

        @Override
        public void handleException(StompSession sesion, StompCommand comando, StompHeaders cabeceras,
                                    byte[] cuerpo, Throwable ex) {
            errores.add("EXCEPCION: " + ex.getMessage());
        }

        @Override
        public void handleTransportError(StompSession sesion, Throwable ex) {
            errores.add("TRANSPORTE: " + ex.getMessage());
        }
    }

    private StompSession conectar(String token, StompSessionHandlerAdapter manejador) throws Exception {
        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer " + token);
        return new WebSocketStompClient(new StandardWebSocketClient())
                .connectAsync("ws://localhost:" + puerto + "/ws", new WebSocketHttpHeaders(), connect, manejador)
                .get(10, TimeUnit.SECONDS);
    }

    /** Suscripcion confirmada con una sonda, como en CanalDeSalaIT: nada de esperas a ojo. */
    private BlockingQueue<String> escuchar(StompSession sesion, String destino) throws Exception {
        BlockingQueue<String> recibidos = new LinkedBlockingQueue<>();
        BlockingQueue<String> sondas = new LinkedBlockingQueue<>();
        sesion.subscribe(destino, manejador(recibidos, sondas));
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
        return recibidos;
    }

    private static StompFrameHandler manejador(BlockingQueue<String> recibidos, BlockingQueue<String> sondas) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders cabeceras) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders cabeceras, Object cuerpo) {
                String texto = new String((byte[]) cuerpo, StandardCharsets.UTF_8);
                (texto.contains(SONDA) ? sondas : recibidos).add(texto);
            }
        };
    }

    private static void seRechazo(Registro registro) throws InterruptedException {
        String rechazo = registro.errores.poll(10, TimeUnit.SECONDS);
        assertNotNull(rechazo, "el servidor tiene que rechazar el frame, no ignorarlo");
        assertTrue(rechazo.startsWith("ERROR") || rechazo.startsWith("TRANSPORTE")
                || rechazo.startsWith("EXCEPCION"), rechazo);
    }

    private static byte[] json(String texto) {
        return ("{\"texto\":\"" + texto + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------- SEND

    @Test
    @DisplayName("un SEND directo al chat general lo rechaza el servidor y no le llega a nadie")
    void sendDirectoAlTemaGeneral() throws Exception {
        BlockingQueue<String> general = escuchar(conectar(ANFITRIONA, new Registro()), Canal.general().destino());
        Registro registro = new Registro();
        StompSession intruso = conectar(AJENO, registro);

        intruso.send("/tema/chat/general", json("me salto la lista negra"));

        seRechazo(registro);
        assertNull(general.poll(2, TimeUnit.SECONDS), "el broker no puede reenviar un SEND a /tema/**");
    }

    @Test
    @DisplayName("un SEND a una cola de usuario tampoco se reenvia")
    void sendDirectoAUnaCola() throws Exception {
        Registro registro = new Registro();
        StompSession intruso = conectar(AJENO, registro);

        intruso.send("/usuario/" + IDENTIDADES.get(ANFITRIONA) + "/cola/salas", json("hola"));

        seRechazo(registro);
    }

    @Test
    @DisplayName("el chat de una sala privada: el ajeno no escribe y nada queda en el historial")
    void chatDeSalaPrivadaAjeno() throws Exception {
        UUID privada = crearSala(true);
        Registro registro = new Registro();
        StompSession intruso = conectar(AJENO, registro);

        intruso.send("/app/salas/" + privada + "/chat", json("me cuelo"));

        seRechazo(registro);
        assertEquals(0, historial.ultimos(Canal.deSala(privada), 50).size());
    }

    // -------------------------------------------------------------- SUBSCRIBE

    @Test
    @DisplayName("una suscripcion con patron no oye los chats de las salas privadas")
    void suscripcionConPatron() throws Exception {
        UUID privada = crearSala(true);
        Registro registro = new Registro();
        StompSession intruso = conectar(AJENO, registro);
        BlockingQueue<String> oidos = new LinkedBlockingQueue<>();
        intruso.subscribe("/tema/salas/**", manejador(oidos, new LinkedBlockingQueue<>()));

        seRechazo(registro);
        mensajeria.convertAndSend(Canal.deSala(privada).destino(), "{\"texto\":\"secreto\"}");
        assertNull(oidos.poll(2, TimeUnit.SECONDS), "el patron no puede registrarse");
    }

    @Test
    @DisplayName("nadie se suscribe a la cola ya resuelta de otra sesion")
    void colaResueltaDeOtraSesion() throws Exception {
        Registro registro = new Registro();
        StompSession intruso = conectar(AJENO, registro);

        intruso.subscribe("/cola/mensajes-directos-user0123456789", manejador(
                new LinkedBlockingQueue<>(), new LinkedBlockingQueue<>()));

        seRechazo(registro);
    }

    @Test
    @DisplayName("el historial de una sala privada no lo lee quien no esta dentro")
    void historialDeSalaPrivada() throws Exception {
        UUID privada = crearSala(true);
        Registro registro = new Registro();
        StompSession intruso = conectar(AJENO, registro);
        BlockingQueue<String> historialLeido = new LinkedBlockingQueue<>();

        intruso.subscribe("/app/salas/" + privada + "/chat/historial",
                manejador(historialLeido, new LinkedBlockingQueue<>()));

        seRechazo(registro);
        assertNull(historialLeido.poll(1, TimeUnit.SECONDS), "no llega ni la respuesta vacia");
    }

    @Test
    @DisplayName("la anfitriona si lee el historial de su sala privada")
    void historialDeSalaPrivadaPropio() throws Exception {
        UUID privada = crearSala(true);
        Registro registro = new Registro();
        StompSession anfitriona = conectar(ANFITRIONA, registro);
        BlockingQueue<String> historialLeido = new LinkedBlockingQueue<>();

        anfitriona.subscribe("/app/salas/" + privada + "/chat/historial",
                manejador(historialLeido, new LinkedBlockingQueue<>()));

        String respuesta = historialLeido.poll(10, TimeUnit.SECONDS);
        assertAll(
                () -> assertNotNull(respuesta, "el historial propio tiene que llegar"),
                () -> assertEquals("[]", respuesta),
                () -> assertNull(registro.errores.poll(1, TimeUnit.SECONDS)));
    }

    @Test
    @DisplayName("el canal de una partida que no existe no se puede seguir")
    void partidaInexistente() throws Exception {
        Registro registro = new Registro();
        StompSession intruso = conectar(AJENO, registro);

        intruso.subscribe("/tema/partidas/" + UUID.randomUUID(), manejador(
                new LinkedBlockingQueue<>(), new LinkedBlockingQueue<>()));

        seRechazo(registro);
    }

    @Test
    @DisplayName("una sala publica sigue igual: cualquiera la sigue y su chat general tambien")
    void loPublicoNoCambia() throws Exception {
        UUID publica = crearSala(false);
        Registro registro = new Registro();
        StompSession ajeno = conectar(AJENO, registro);

        BlockingQueue<String> sala = escuchar(ajeno, "/tema/salas/" + publica);
        BlockingQueue<String> general = escuchar(ajeno, Canal.general().destino());

        mensajeria.convertAndSend("/tema/salas/" + publica, "{\"tipo\":\"sala.participante.ingreso\"}");
        mensajeria.convertAndSend(Canal.general().destino(), "{\"texto\":\"hola\"}");
        assertAll(
                () -> assertNotNull(sala.poll(10, TimeUnit.SECONDS)),
                () -> assertNotNull(general.poll(10, TimeUnit.SECONDS)),
                () -> assertNull(registro.errores.poll(1, TimeUnit.SECONDS)));
    }
}
