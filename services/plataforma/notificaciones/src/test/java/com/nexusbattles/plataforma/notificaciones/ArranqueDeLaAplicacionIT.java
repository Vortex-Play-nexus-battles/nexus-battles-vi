package com.nexusbattles.plataforma.notificaciones;

import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.notificaciones.bandeja.CanalDeNotificaciones;
import com.nexusbattles.plataforma.notificaciones.bandeja.NotificacionesController;
import com.nexusbattles.plataforma.notificaciones.catalogo.CursorCatalogoRepository;
import com.nexusbattles.plataforma.notificaciones.catalogo.RegistroDeCursorCatalogo;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * <p>Y el de HU-NOT-001 CA-02, marcar todas como leidas: la sentencia en
 * bloque solo se puede probar de verdad contra PostgreSQL.
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

    /** HU-NOT-001: la tabla de V3 y sus dos escrituras, contra PostgreSQL real. */
    @Autowired
    private CursorCatalogoRepository cursores;

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

    @Test
    @DisplayName("auditoria 30-sep: un identificador de torneos (mas de 100 caracteres) se guarda y no se confunde con un duplicado")
    void identificadorLargoDeTorneos() throws Exception {
        UUID ana = UUID.randomUUID();
        String id = "torneo-" + UUID.randomUUID() + "-jugador-" + ana + "-aviso-inscripcion";
        assertTrue(id.length() > 100, "el caso real mide " + id.length());

        HttpResponse<String> primera = emitir(ana, id);
        assertEquals(201, primera.statusCode(), primera.body());
        // El reintento del mismo evento sigue siendo el duplicado de siempre.
        HttpResponse<String> repetida = emitir(ana, id);
        assertEquals(409, repetida.statusCode(), repetida.body());
        // Y lo que no cabe se rechaza como dato invalido, no como «ya estaba».
        HttpResponse<String> enorme = emitir(ana, "x".repeat(201));
        assertEquals(400, enorme.statusCode(), enorme.body());

        HttpResponse<String> bandeja = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/users/" + ana + "/notifications"))
                        .header("Authorization", "Bearer " + emisor.tokenDeJugador("Ana", ana)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, bandeja.statusCode(), bandeja.body());
        assertTrue(bandeja.body().contains(id), "el aviso esta en la bandeja: " + bandeja.body());
        assertTrue(bandeja.body().contains("\"noLeidas\":1"), bandeja.body());
    }

    @Test
    @DisplayName("HU-NOT-001 CA-02 de punta a punta: marcar todas deja la bandeja sin no leidos de una vez, avisa a la sesion abierta y repetirlo marca 0")
    void marcarTodasLeidasDePuntaAPunta() throws Exception {
        UUID ana = UUID.randomUUID();
        UUID otro = UUID.randomUUID();
        String tokenDeAna = emisor.tokenDeJugador("Ana", ana);
        String tokenDelOtro = emisor.tokenDeJugador("Otro", otro);

        StompSession sesionDeAna = conectar(tokenDeAna);
        BlockingQueue<String> colaDeAna = suscribirse(sesionDeAna);
        sesionDeAna.send("/app/notificaciones/sesion",
                "{\"sesionId\":\"escritorio\"}".getBytes(StandardCharsets.UTF_8));
        assertNotNull(colaDeAna.poll(5, TimeUnit.SECONDS), "el alta de sesion responde con el contador");

        for (int i = 1; i <= 3; i++) {
            HttpResponse<String> emision = emitir(ana, "aviso-" + i + "-" + UUID.randomUUID());
            assertEquals(201, emision.statusCode(), emision.body());
        }
        assertEquals(201, emitir(otro, "aviso-del-otro-" + UUID.randomUUID()).statusCode());
        assertNotNull(esperarContador(colaDeAna, 3), "antes de marcar, Ana tiene tres sin leer");

        // Los tres de una vez, y el contador en 0 llega por el canal.
        HttpResponse<String> marcado = marcarTodas(ana, tokenDeAna);
        assertEquals(200, marcado.statusCode(), marcado.body());
        assertTrue(marcado.body().contains("\"marcadas\":3"), marcado.body());
        assertTrue(marcado.body().contains("\"noLeidas\":0"), marcado.body());
        assertNotNull(esperarContador(colaDeAna, 0), "la sesion abierta recibe el contador en 0");

        // Sin estado a medias: ningun aviso de Ana queda sin leer.
        HttpResponse<String> bandeja = bandejaDe(ana, tokenDeAna);
        assertEquals(200, bandeja.statusCode(), bandeja.body());
        assertTrue(bandeja.body().contains("\"noLeidas\":0"), bandeja.body());
        assertFalse(bandeja.body().contains("\"leida\":false"), bandeja.body());

        // Idempotente: la segunda vez responde igual de bien y no marca nada.
        HttpResponse<String> otraVez = marcarTodas(ana, tokenDeAna);
        assertEquals(200, otraVez.statusCode(), otraVez.body());
        assertTrue(otraVez.body().contains("\"marcadas\":0"), otraVez.body());
        assertTrue(otraVez.body().contains("\"noLeidas\":0"), otraVez.body());

        // Solo la bandeja de Ana: la del otro sigue igual, y el no puede marcar la ajena.
        HttpResponse<String> delOtro = bandejaDe(otro, tokenDelOtro);
        assertTrue(delOtro.body().contains("\"noLeidas\":1"), delOtro.body());
        assertEquals(403, marcarTodas(ana, tokenDelOtro).statusCode(), "la bandeja ajena no se marca");
    }

    @Test
    @DisplayName("HU-NOT-001 CA-02: varias sesiones que marcan todas a la vez no cuentan dos veces el mismo aviso")
    void marcarTodasALaVezNoCuentaDosVeces() throws Exception {
        UUID ana = UUID.randomUUID();
        String tokenDeAna = emisor.tokenDeJugador("Ana", ana);
        for (int i = 1; i <= 3; i++) {
            HttpResponse<String> emision = emitir(ana, "aviso-" + i + "-" + UUID.randomUUID());
            assertEquals(201, emision.statusCode(), emision.body());
        }

        // Seis pestanas a la vez. Si la cuenta saliera de la bandeja cargada,
        // las que se solapan contarian los mismos avisos; con la sentencia en
        // bloque, cada aviso lo cuenta una sola llamada.
        List<CompletableFuture<HttpResponse<String>>> enVuelo = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            enVuelo.add(http.sendAsync(peticionMarcarTodas(ana, tokenDeAna), HttpResponse.BodyHandlers.ofString()));
        }
        int marcadas = 0;
        for (CompletableFuture<HttpResponse<String>> llamada : enVuelo) {
            HttpResponse<String> respuesta = llamada.get(10, TimeUnit.SECONDS);
            assertEquals(200, respuesta.statusCode(), respuesta.body());
            marcadas += numero(respuesta.body(), "marcadas");
        }

        assertEquals(3, marcadas, "entre todas marcan los tres, ni uno mas");
        HttpResponse<String> bandeja = bandejaDe(ana, tokenDeAna);
        assertTrue(bandeja.body().contains("\"noLeidas\":0"), bandeja.body());
    }

    @Test
    @DisplayName("HU-NOT-001 (V3): el cursor de avisos del catalogo se crea, solo avanza y anota la consulta, contra PostgreSQL")
    void cursorDeAvisosDelCatalogo() {
        String uid = UUID.randomUUID().toString();
        Instant base = Instant.parse("2026-10-05T12:00:00.123Z");

        assertEquals(1, cursores.guardar(uid, base, base), "el primer cursor se inserta");
        // Una sesion que llega tarde con un hasta anterior no lo hace retroceder.
        cursores.guardar(uid, base.minusSeconds(60), base.plusSeconds(5));
        RegistroDeCursorCatalogo cursor = cursores.findById(uid).orElseThrow();
        assertEquals(base, cursor.getHasta(), "GREATEST: el cursor no retrocede");
        assertEquals(base.plusSeconds(5), cursor.getConsultadoEn());

        cursores.guardar(uid, base.plusSeconds(60), base.plusSeconds(10));
        assertEquals(base.plusSeconds(60), cursores.findById(uid).orElseThrow().getHasta(), "y avanza");

        // Anotar una consulta fallida solo toca consultado_en.
        assertEquals(1, cursores.marcarConsulta(uid, base.plusSeconds(99)));
        RegistroDeCursorCatalogo anotado = cursores.findById(uid).orElseThrow();
        assertEquals(base.plusSeconds(60), anotado.getHasta());
        assertEquals(base.plusSeconds(99), anotado.getConsultadoEn());
        assertEquals(0, cursores.marcarConsulta(UUID.randomUUID().toString(), base),
                "sin cursor no hay nada que anotar");
    }

    private HttpRequest peticionMarcarTodas(UUID dueno, String token) {
        return HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + puerto + "/api/v1/users/" + dueno + "/notifications/read"))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody()).build();
    }

    private HttpResponse<String> marcarTodas(UUID dueno, String token) throws Exception {
        return http.send(peticionMarcarTodas(dueno, token), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> bandejaDe(UUID dueno, String token) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/users/" + dueno + "/notifications"))
                        .header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Un campo entero de una respuesta JSON plana. */
    private static int numero(String json, String campo) {
        Matcher valor = Pattern.compile("\"" + campo + "\":(\\d+)").matcher(json);
        assertTrue(valor.find(), "falta " + campo + " en " + json);
        return Integer.parseInt(valor.group(1));
    }

    /** El contador con ese valor, saltando los avisos y los contadores anteriores. */
    private static String esperarContador(BlockingQueue<String> cola, int noLeidas) throws InterruptedException {
        String buscado = "\"noLeidas\":" + noLeidas + "}";
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < limite) {
            String mensaje = cola.poll(1, TimeUnit.SECONDS);
            if (mensaje != null && mensaje.contains(buscado)) {
                return mensaje;
            }
        }
        return null;
    }

    private HttpResponse<String> emitir(UUID destinatario, String id) throws Exception {
        String evento = """
                {"usuarioId":"%s","id":"%s","tipo":"TORNEO","titulo":"Inscripcion confirmada",
                 "cuerpo":"Tu equipo quedo inscrito.","creadaEn":"2026-10-02T12:00:00Z"}
                """.formatted(destinatario, id);
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/internal/notifications"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + emisor.tokenDeServicio("torneos"))
                        .POST(HttpRequest.BodyPublishers.ofString(evento)).build(),
                HttpResponse.BodyHandlers.ofString());
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
