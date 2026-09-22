package com.nexusbattles.plataforma.notificaciones;

import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.notificaciones.bandeja.CanalDeNotificaciones;
import com.nexusbattles.plataforma.notificaciones.bandeja.NotificacionesController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Levanta la aplicacion COMPLETA, igual que hace el contenedor en el
 * servidor: Flyway sobre PostgreSQL, JPA, el servidor web, el canal STOMP y
 * la cadena de seguridad, con el JWKS apuntando al emisor de prueba.
 *
 * <p>Por que existe: el 2026-09-17 comentarios no arranco en el host con el
 * build en verde, porque ninguna de sus pruebas cargaba el contexto de
 * Spring. Este modulo es el que mas superficie de arranque tiene del bloque:
 * WebSocket, JPA, Flyway y ahora el servidor de recursos a la vez.
 *
 * <p>Ademas hace el recorrido completo de HU-NOT-006 con identidad real: un
 * jugador conecta el canal con su JWT, da de alta su sesion, un servicio
 * emite un aviso con su credencial y el aviso llega a la cola privada del
 * jugador, y solo a la suya. Es lo que antes no se podia afirmar: la
 * identidad del canal salia de {@code ?usuario=} en la URL.
 *
 * <p>Con {@code ddl-auto=validate}: asi Hibernate compara su mapeo contra las
 * columnas que dejo Flyway. Sin {@code disabledWithoutDocker}: una prueba de
 * integracion omitida no es una prueba que pasa, es una que no se ejecuto.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.hibernate.ddl-auto=validate"
)
class ArranqueDeLaAplicacionIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();
    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private ApplicationContext contexto;

    @LocalServerPort
    private int puerto;

    @Test
    @DisplayName("el contexto de la aplicacion arranca por completo")
    void arranca() {
        assertAll(
                () -> assertNotNull(contexto),
                () -> assertNotNull(contexto.getBean(NotificacionesController.class))
        );
    }

    @Test
    @DisplayName("el canal STOMP de la bandeja queda publicado")
    void hayCanalEnTiempoReal() {
        assertAll(
                () -> assertNotNull(contexto.getBean(SimpMessagingTemplate.class)),
                () -> assertNotNull(contexto.getBean(CanalDeNotificaciones.class))
        );
    }

    @Test
    @DisplayName("el handshake esta abierto, pero un CONNECT sin token no obtiene sesion")
    void elConnectExigeToken() {
        assertThrows(Exception.class, () -> conectar(null),
                "sin JWT en el CONNECT el servidor debe responder ERROR y no abrir sesion");
    }

    @Test
    @DisplayName("de punta a punta: el aviso que emite un servicio llega por la cola privada del uid del token, y solo a el")
    void elAvisoLlegaAlDuenoYSoloAEl() throws Exception {
        UUID ana = UUID.randomUUID();
        UUID otro = UUID.randomUUID();
        StompSession sesionDeAna = conectar(emisor.tokenDeJugador("Ana", ana));
        StompSession sesionDeOtro = conectar(emisor.tokenDeJugador("Otro", otro));
        BlockingQueue<String> colaDeAna = suscribirse(sesionDeAna);
        BlockingQueue<String> colaDeOtro = suscribirse(sesionDeOtro);

        // Alta de sesion: el usuarioId del mensaje se ignora a proposito.
        sesionDeAna.send("/app/notificaciones/sesion",
                "{\"usuarioId\":\"suplantado\",\"sesionId\":\"escritorio\"}".getBytes(StandardCharsets.UTF_8));
        String contador = colaDeAna.poll(5, TimeUnit.SECONDS);
        assertNotNull(contador, "el alta de sesion responde con el contador de no leidos");

        // Emision por un servicio, con credencial de servicio (ADR-005).
        String evento = """
                {"usuarioId":"%s","id":"evt-%s","tipo":"subasta","titulo":"Tu puja fue superada",
                 "cuerpo":"Alguien pujo mas alto.","creadaEn":"2026-09-21T05:00:00Z"}
                """.formatted(ana, UUID.randomUUID());
        HttpResponse<String> emision = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/internal/notifications"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + emisor.tokenDeServicio("ms-subastas"))
                        .POST(HttpRequest.BodyPublishers.ofString(evento)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, emision.statusCode(), emision.body());

        String recibido = esperarAviso(colaDeAna);
        assertNotNull(recibido, "Ana debe recibir el aviso por su cola privada");
        assertTrue(recibido.contains("Tu puja fue superada"), recibido);
        assertEquals(null, colaDeOtro.poll(1, TimeUnit.SECONDS), "el otro jugador no recibe nada");

        // Y por HTTP: Ana ve su bandeja con el aviso; el otro no puede verla.
        HttpResponse<String> bandeja = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/users/" + ana + "/notifications"))
                        .header("Authorization", "Bearer " + emisor.tokenDeJugador("Ana", ana)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, bandeja.statusCode(), bandeja.body());
        assertTrue(bandeja.body().contains("\"noLeidas\":1"), bandeja.body());

        HttpResponse<String> ajena = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/users/" + ana + "/notifications"))
                        .header("Authorization", "Bearer " + emisor.tokenDeJugador("Otro", otro)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, ajena.statusCode(), "la bandeja ajena no se lee");

        HttpResponse<String> sinCredencial = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/internal/notifications"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(evento)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, sinCredencial.statusCode(), "emitir sin credencial de servicio es 401");
    }

    /** Un aviso, saltando los mensajes de contador que el alta y la emision tambien mandan. */
    private static String esperarAviso(BlockingQueue<String> cola) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < limite) {
            String mensaje = cola.poll(1, TimeUnit.SECONDS);
            if (mensaje != null && mensaje.contains("\"titulo\"")) {
                return mensaje;
            }
        }
        return null;
    }

    private StompSession conectar(String token) throws Exception {
        StompHeaders connect = new StompHeaders();
        if (token != null) {
            connect.add("Authorization", "Bearer " + token);
        }
        return new WebSocketStompClient(new StandardWebSocketClient())
                .connectAsync("ws://localhost:" + puerto + "/ws/notificaciones", new WebSocketHttpHeaders(),
                        connect, new StompSessionHandlerAdapter() { })
                .get(10, TimeUnit.SECONDS);
    }

    private static BlockingQueue<String> suscribirse(StompSession sesion) {
        BlockingQueue<String> recibidos = new LinkedBlockingQueue<>();
        sesion.subscribe("/usuario/cola/notificaciones", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                recibidos.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        return recibidos;
    }
}
