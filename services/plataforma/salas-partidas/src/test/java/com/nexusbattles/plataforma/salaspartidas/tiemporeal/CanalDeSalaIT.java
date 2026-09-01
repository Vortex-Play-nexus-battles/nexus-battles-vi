package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Canal de sala extremo a extremo — HU-SAL-002, tercer criterio del issue #30.
 *
 * <p>Prueba el viaje completo con el servicio levantado de verdad: un cliente
 * se conecta por STOMP y se suscribe a {@code /tema/salas/{idSala}}, otro
 * jugador entra por la API REST, y el primero recibe
 * {@code sala.participante.ingreso}. Nada esta simulado: hay un servidor con
 * puerto, un socket, una PostgreSQL 17 por Testcontainers y el broker real.
 *
 * <p><b>La seguridad no se debilita.</b> La cadena de filtros queda intacta: el
 * handshake sigue exigiendo un token y {@code /api/v1/salas/**} sigue exigiendo
 * rol JUGADOR — el tercer caso lo comprueba. Lo unico que se sustituye es el
 * {@link JwtDecoder}, que en produccion valida contra Keycloak; sin eso la
 * prueba necesitaria un Keycloak levantado para comprobar algo que no es de
 * Keycloak. Los tokens de prueba llevan el rol en {@code realm_access.roles},
 * exactamente donde lo busca {@code ConversorRolesJwt}.
 *
 * <p><b>Sin Jackson y sin conversor de mensajes.</b> Spring Boot 4 no trae
 * {@code com.fasterxml.jackson.databind} en el classpath de este servicio, y
 * {@code MappingJackson2MessageConverter} esta marcado para eliminacion. El
 * cliente se queda con el conversor por defecto y lee el cuerpo como bytes: se
 * comprueba el JSON literal que viaja por el cable, que para una prueba de
 * contrato es mejor evidencia que un objeto ya deserializado. El identificador
 * de la sala sale de la cabecera {@code Location}, no de parsear el cuerpo.
 *
 * <p>Sin {@code disabledWithoutDocker}, por el mismo motivo que
 * {@code RepositorioSalasJpaIT}: una prueba de integracion omitida no es una
 * prueba que pasa.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CanalDeSalaIT.SeguridadDePrueba.class)
@DisplayName("Canal de sala extremo a extremo (HU-SAL-002)")
class CanalDeSalaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String ANFITRION = "anfitrion";
    private static final String VISITANTE = "visitante";

    private static final UUID ID_ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ID_VISITANTE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Value("${local.server.port}")
    private int puerto;

    /** El mismo bean que usa el adaptador en produccion; aqui, para la sonda. */
    @Autowired
    private SimpMessagingTemplate mensajeria;

    private final HttpClient http = HttpClient.newHttpClient();

    /**
     * Decodificador de prueba: traduce un token literal a la identidad que
     * representa, con el rol donde lo espera la cadena de seguridad real.
     */
    @TestConfiguration
    static class SeguridadDePrueba {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(ANFITRION.equals(token) ? ID_ANFITRION.toString() : ID_VISITANTE.toString())
                    .claim("realm_access", Map.of("roles", List.of("JUGADOR")))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
        }
    }

    private HttpResponse<String> pedir(String ruta, String cuerpo, String token) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(cuerpo == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        return http.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Crea una sala real por la API y devuelve su identificador.
     *
     * <p>El identificador se lee de la cabecera {@code Location}, que el propio
     * contrato obliga a devolver en el 201. Asi la prueba no necesita ningun
     * analizador de JSON.
     */
    private UUID crearSala() throws Exception {
        HttpResponse<String> respuesta = pedir("/api/v1/salas", """
                {"maximoParticipantes": 4, "modalidad": "HASTA_SEIS", "recompensaCreditos": 0}
                """, ANFITRION);

        assertEquals(201, respuesta.statusCode(), "la sala de partida tiene que crearse");

        String ubicacion = respuesta.headers().firstValue("Location").orElseThrow();
        return UUID.fromString(ubicacion.substring(ubicacion.lastIndexOf('/') + 1));
    }

    /** Cliente STOMP con el conversor por defecto: sin Jackson de por medio. */
    private static WebSocketStompClient clienteStomp() {
        return new WebSocketStompClient(new StandardWebSocketClient());
    }

    private StompSession conectar(String token) throws Exception {
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.add("Authorization", "Bearer " + token);

        return clienteStomp()
                .connectAsync("ws://localhost:" + puerto + "/ws", handshake,
                        new StompSessionHandlerAdapter() { })
                .get(10, TimeUnit.SECONDS);
    }

    /** Marca de la sonda con la que se confirma que la suscripcion esta viva. */
    private static final String SONDA = "sonda-de-suscripcion";

    /**
     * Se suscribe al canal de una sala y NO vuelve hasta que la suscripcion
     * esta demostrablemente activa.
     *
     * <p>Nada de esperas a ojo. La suscripcion viaja de forma asincrona, asi
     * que se publica una sonda por el mismo destino hasta que vuelve: el
     * momento en que la sonda llega es la prueba de que el broker ya registro
     * la suscripcion. Sin esto, el ingreso podria publicarse antes y el mensaje
     * se perderia por una carrera, no por un fallo real.
     *
     * <p>Se usa una sonda y no el frame RECEIPT de STOMP porque el broker
     * simple en memoria no garantiza emitirlo, y una prueba que depende de algo
     * no garantizado es justo lo que se intenta evitar. Las sondas se separan
     * de los avisos en el propio manejador, para que no contaminen la cola que
     * de verdad se comprueba.
     */
    private BlockingQueue<String> suscribirseA(StompSession sesion, UUID idSala) throws Exception {
        BlockingQueue<String> avisos = new LinkedBlockingQueue<>();
        BlockingQueue<String> sondas = new LinkedBlockingQueue<>();
        String destino = CanalDeSalaStomp.destinoDe(idSala);

        sesion.subscribe(destino, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders cabeceras) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders cabeceras, Object cuerpo) {
                String texto = new String((byte[]) cuerpo, StandardCharsets.UTF_8);
                (texto.contains(SONDA) ? sondas : avisos).add(texto);
            }
        });

        boolean viva = false;
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!viva && System.nanoTime() < limite) {
            mensajeria.convertAndSend(destino, SONDA);
            viva = sondas.poll(200, TimeUnit.MILLISECONDS) != null;
        }
        assertTrue(viva, "la suscripcion al canal de la sala nunca quedo activa");

        // Descarta las sondas que quedaran en vuelo antes de medir nada.
        while (sondas.poll(200, TimeUnit.MILLISECONDS) != null) {
            // vaciando
        }
        return avisos;
    }

    @Test
    @DisplayName("quien esta suscrito recibe el ingreso con el payload del contrato")
    void elIngresoLlegaAQuienEstaDentro() throws Exception {
        UUID idSala = crearSala();
        BlockingQueue<String> recibidos = suscribirseA(conectar(ANFITRION), idSala);

        HttpResponse<String> ingreso =
                pedir("/api/v1/salas/" + idSala + "/participantes", null, VISITANTE);
        assertEquals(200, ingreso.statusCode(), "el ingreso tiene que aceptarse");

        String aviso = recibidos.poll(10, TimeUnit.SECONDS);

        assertNotNull(aviso, "el mensaje no llego por el canal");
        assertAll(
                () -> assertTrue(aviso.contains("\"tipo\":\"sala.participante.ingreso\""), aviso),
                () -> assertTrue(aviso.contains("\"idSala\":\"" + idSala + "\""), aviso),
                () -> assertTrue(aviso.contains("\"idJugador\":\"" + ID_VISITANTE + "\""), aviso),
                () -> assertTrue(aviso.contains("\"ocupacion\":{\"actual\":2,\"maximo\":4}"), aviso),
                // El contrato se recorto en esta historia: ni heroe ni apodo.
                () -> assertTrue(!aviso.contains("heroe") && !aviso.contains("apodo"), aviso));
    }

    @Test
    @DisplayName("un ingreso rechazado no publica nada por el canal")
    void unRechazoNoViajaPorElCanal() throws Exception {
        UUID idSala = crearSala();
        BlockingQueue<String> recibidos = suscribirseA(conectar(VISITANTE), idSala);

        // El anfitrion ya esta dentro: entrar otra vez se rechaza con 409.
        HttpResponse<String> repetido =
                pedir("/api/v1/salas/" + idSala + "/participantes", null, ANFITRION);
        assertEquals(409, repetido.statusCode());

        assertNull(recibidos.poll(2, TimeUnit.SECONDS),
                "un rechazo no debe anunciarse a los que estan dentro");
    }

    @Test
    @DisplayName("el handshake sin token se rechaza: la seguridad sigue puesta")
    void elHandshakeExigeToken() {
        assertThrows(Exception.class, () -> clienteStomp()
                .connectAsync("ws://localhost:" + puerto + "/ws", new WebSocketHttpHeaders(),
                        new StompSessionHandlerAdapter() { })
                .get(10, TimeUnit.SECONDS));
    }
}
