package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mensajes privados extremo a extremo — B6 (FEEDBACK DEL PROFESOR, no
 * requisito del documento).
 *
 * <p>Servidor con puerto, STOMP de verdad, PostgreSQL 17 por Testcontainers y
 * los <b>clientes HTTP reales</b> de identidad, lista negra, sanciones y
 * notificaciones hablando con un doble HTTP que responde las formas de sus
 * contratos. Ningun puerto de dominio se sustituye: lo unico fingido son los
 * modulos ajenos, y por HTTP. Lo unico que no se prueba aqui es el emisor de
 * tokens (un {@link JwtDecoder} de prueba, como en las demas IT del servicio).
 *
 * <p>Cada prueba usa remitentes propios: el limite de frecuencia es por
 * remitente y vive en la memoria del servicio, que las pruebas comparten.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MensajesDirectosIT.SeguridadDePrueba.class)
@DisplayName("Mensajes privados extremo a extremo (B6)")
class MensajesDirectosIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    /** Token literal = apodo; el uid sale de aqui, como en ms-identidad (sub = apodo, uid aparte). */
    private static final Map<String, UUID> JUGADORES = Map.of(
            "ana", UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "bruno", UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "carla", UUID.fromString("33333333-3333-3333-3333-333333333333"),
            "dario", UUID.fromString("44444444-4444-4444-4444-444444444444"),
            "eva", UUID.fromString("55555555-5555-5555-5555-555555555555"),
            "fede", UUID.fromString("66666666-6666-6666-6666-666666666666"),
            "gala", UUID.fromString("77777777-7777-7777-7777-777777777777"),
            "hugo", UUID.fromString("88888888-8888-8888-8888-888888888888"),
            "ines", UUID.fromString("99999999-9999-9999-9999-999999999999"),
            // Nunca se conecta: es quien recibe el aviso en la bandeja.
            "julia", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

    /** Dario tiene una suspension vigente en moderacion-sanciones. */
    private static final Set<String> SANCIONADOS = Set.of("dario");

    private static final String TERMINO_VETADO = "palabravetadae2e";

    private static HttpServer modulosAjenos;
    private static final List<String> VERIFICACIONES = new CopyOnWriteArrayList<>();
    private static final List<String> AVISOS = new CopyOnWriteArrayList<>();
    private static final List<String> INTENTOS_DE_AVISO = new CopyOnWriteArrayList<>();
    private static final Set<String> IDS_DE_AVISO = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> AUTORIZACIONES = new ConcurrentHashMap<>();

    @BeforeAll
    static void levantarModulosAjenos() throws IOException {
        modulosAjenos = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // ms-identidad-admin.yaml: GET /api/v1/internal/usuarios/{uid}/contacto
        modulosAjenos.createContext("/api/v1/internal/usuarios/", intercambio -> {
            String ruta = intercambio.getRequestURI().getPath();
            String uid = ruta.substring("/api/v1/internal/usuarios/".length(), ruta.length() - "/contacto".length());
            String apodo = JUGADORES.entrySet().stream()
                    .filter(e -> e.getValue().toString().equals(uid)).map(Map.Entry::getKey).findFirst().orElse(null);
            if (apodo == null) {
                responder(intercambio, 404, "{\"type\":\"about:blank\",\"title\":\"Cuenta no encontrada\",\"status\":404}");
                return;
            }
            responder(intercambio, 200, "{\"uid\":\"" + uid + "\",\"email\":\"" + apodo
                    + "@nexus.test\",\"apodo\":\"" + apodo + "\",\"estado\":\"ACTIVO\"}");
        });
        // moderacion-lista-negra.yaml 2.0.0: POST /api/v1/lista-negra/verificar
        modulosAjenos.createContext("/api/v1/lista-negra/verificar", intercambio -> {
            String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            VERIFICACIONES.add(cuerpo);
            boolean vetado = cuerpo.contains(TERMINO_VETADO);
            responder(intercambio, 200, vetado
                    ? "{\"aprobado\":false,\"accion\":\"BLOQUEAR\",\"motivo\":\"El mensaje no esta permitido.\"}"
                    : "{\"aprobado\":true,\"accion\":\"PERMITIR\"}");
        });
        // moderacion-sanciones-consulta.yaml 1.4.0: GET /api/v1/sanciones/usuarios/{uid}/activa
        modulosAjenos.createContext("/api/v1/sanciones/usuarios/", intercambio -> {
            String ruta = intercambio.getRequestURI().getPath();
            boolean sancionado = SANCIONADOS.stream().map(JUGADORES::get).anyMatch(uid -> ruta.contains(uid.toString()));
            responder(intercambio, 200, sancionado
                    ? "{\"sancionActiva\":true,\"motivo\":\"Lenguaje ofensivo\",\"tipo\":\"SUSPENSION\"}"
                    : "{\"sancionActiva\":false}");
        });
        // notificaciones.yaml 1.1.0: POST /api/v1/internal/notifications. Como
        // el servicio de verdad: un id repetido en la bandeja de ese usuario
        // es 409 y no se guarda otra vez.
        modulosAjenos.createContext("/api/v1/internal/notifications", intercambio -> {
            String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            INTENTOS_DE_AVISO.add(cuerpo);
            if (!IDS_DE_AVISO.add(campo(cuerpo, "usuarioId") + "|" + campo(cuerpo, "id"))) {
                responder(intercambio, 409, "{\"status\":409}");
                return;
            }
            AVISOS.add(cuerpo);
            responder(intercambio, 201, "{}");
        });
        modulosAjenos.start();
    }

    private static void responder(HttpExchange intercambio, int estado, String json) throws IOException {
        String autorizacion = intercambio.getRequestHeaders().getFirst("Authorization");
        AUTORIZACIONES.put(intercambio.getRequestURI().getPath(), autorizacion == null ? "" : autorizacion);
        byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, cuerpo.length);
        try (var salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }

    @AfterAll
    static void apagarModulosAjenos() {
        if (modulosAjenos != null) {
            modulosAjenos.stop(0);
        }
    }

    @DynamicPropertySource
    static void apuntarAlDoble(DynamicPropertyRegistry registro) {
        String base = "http://127.0.0.1:" + modulosAjenos.getAddress().getPort();
        registro.add("chat.lista-negra.url", () -> base + "/api/v1/lista-negra/verificar");
        registro.add("salas.sanciones.url", () -> base + "/api/v1");
        registro.add("mensajes-directos.identidad.url", () -> base);
        registro.add("mensajes-directos.identidad.cache-segundos", () -> "0");
        registro.add("mensajes-directos.notificaciones.url", () -> base + "/api/v1");
    }

    @TestConfiguration
    static class SeguridadDePrueba {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(token)
                    .claim("uid", JUGADORES.get(token).toString())
                    .claim("rol", "JUGADOR")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
        }
    }

    @Value("${local.server.port}")
    private int puerto;

    /** El registro de usuarios del broker: la sonda de las suscripciones a colas de usuario. */
    @Autowired
    private SimpUserRegistry registroDeUsuarios;

    private final HttpClient http = HttpClient.newHttpClient();

    /** Sesiones abiertas por la prueba en curso; se cierran al terminar para no contaminar la siguiente. */
    private final List<StompSession> abiertas = new CopyOnWriteArrayList<>();

    @AfterEach
    void cerrarSesiones() throws InterruptedException {
        for (StompSession sesion : abiertas) {
            if (sesion.isConnected()) {
                sesion.disconnect();
            }
        }
        abiertas.clear();
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (registroDeUsuarios.getUserCount() > 0 && System.nanoTime() < limite) {
            Thread.sleep(50);
        }
    }

    // ------------------------------------------------------------------ ayudas

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

    private StompSession conectar(String token, Registro registro) throws Exception {
        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer " + token);
        StompSession sesion = new WebSocketStompClient(new StandardWebSocketClient())
                .connectAsync("ws://localhost:" + puerto + "/ws", new WebSocketHttpHeaders(), connect, registro)
                .get(10, TimeUnit.SECONDS);
        abiertas.add(sesion);
        return sesion;
    }

    /** Cuantas sesiones de ese jugador escuchan ahora su cola de mensajes privados. */
    private long escuchando(String token) {
        SimpUser usuario = registroDeUsuarios.getUser(JUGADORES.get(token).toString());
        if (usuario == null) {
            return 0;
        }
        return usuario.getSessions().stream()
                .flatMap(sesion -> sesion.getSubscriptions().stream())
                .filter(suscripcion -> "/usuario/cola/mensajes-directos".equals(suscripcion.getDestination()))
                .count();
    }

    /**
     * Se suscribe a su cola de mensajes privados y no vuelve hasta que el
     * servidor la tiene registrada: la sonda es el propio registro de usuarios
     * del broker, el mismo que decide si hace falta aviso en la bandeja. Se
     * espera a que haya una suscripcion MAS que antes, por si el jugador ya
     * tenia otra abierta.
     */
    private BlockingQueue<String> escucharSuCola(String token, StompSession sesion) throws Exception {
        long antes = escuchando(token);
        BlockingQueue<String> recibidos = new LinkedBlockingQueue<>();
        sesion.subscribe("/usuario/cola/mensajes-directos", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders cabeceras) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders cabeceras, Object cuerpo) {
                recibidos.add(new String((byte[]) cuerpo, StandardCharsets.UTF_8));
            }
        });
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (escuchando(token) <= antes && System.nanoTime() < limite) {
            Thread.sleep(50);
        }
        assertTrue(escuchando(token) > antes, token + " nunca quedo escuchando su cola");
        return recibidos;
    }

    private static void escribir(StompSession sesion, String destinatario, String texto, String idCliente) {
        String json = idCliente == null
                ? "{\"texto\":\"" + texto + "\"}"
                : "{\"texto\":\"" + texto + "\",\"idCliente\":\"" + idCliente + "\"}";
        sesion.send("/app/mensajes-directos/" + JUGADORES.getOrDefault(destinatario, UUID.nameUUIDFromBytes(
                destinatario.getBytes(StandardCharsets.UTF_8))), json.getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> rest(String metodo, String ruta, String token, String cuerpo) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
        peticion.method(metodo, cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo));
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String conversaciones() {
        return "/api/v1/mensajes-directos/conversaciones";
    }

    // ------------------------------------------------------------------ casos

    /** Los no leidos de la conversacion con {@code uidOtro} en la respuesta de «mis conversaciones». */
    private static int noLeidosCon(String cuerpo, UUID uidOtro) {
        Matcher fila = Pattern
                .compile("\\{\"uidOtro\":\"" + uidOtro + "\".*?\"noLeidos\":(\\d+)}")
                .matcher(cuerpo);
        assertTrue(fila.find(), "no hay conversacion con " + uidOtro + ": " + cuerpo);
        return Integer.parseInt(fila.group(1));
    }

    @Test
    @DisplayName("A escribe a B: B lo recibe en vivo, A recibe su eco y los dos lo ven en el historial")
    void aEscribeAByBLoRecibe() throws Exception {
        // Las pruebas comparten base: se parte de «Bruno ya leyo todo lo de Ana».
        rest("POST", conversaciones() + "/" + JUGADORES.get("ana") + "/leido", "bruno", null);
        StompSession bruno = conectar("bruno", new Registro());
        BlockingQueue<String> colaDeBruno = escucharSuCola("bruno", bruno);
        StompSession ana = conectar("ana", new Registro());
        BlockingQueue<String> colaDeAna = escucharSuCola("ana", ana);

        escribir(ana, "bruno", "hola bruno", "cli-1");

        String recibido = colaDeBruno.poll(10, TimeUnit.SECONDS);
        String eco = colaDeAna.poll(10, TimeUnit.SECONDS);
        assertNotNull(recibido, "B tiene que recibirlo en tiempo real");
        assertNotNull(eco, "A tiene que recibir su eco");
        UUID idAna = JUGADORES.get("ana");
        UUID idBruno = JUGADORES.get("bruno");
        assertAll(
                () -> assertTrue(recibido.contains("\"tipo\":\"MENSAJE\""), recibido),
                () -> assertTrue(recibido.contains("\"remitente\":\"" + idAna + "\""), recibido),
                () -> assertTrue(recibido.contains("\"apodoRemitente\":\"ana\""), recibido),
                () -> assertTrue(recibido.contains("\"destinatario\":\"" + idBruno + "\""), recibido),
                () -> assertTrue(recibido.contains("\"conversacion\":\"dm:" + idAna + ":" + idBruno + "\""), recibido),
                () -> assertTrue(recibido.contains("\"texto\":\"hola bruno\""), recibido),
                () -> assertFalse(recibido.contains("cli-1"), "el idCliente es del autor: " + recibido),
                () -> assertTrue(eco.contains("\"idCliente\":\"cli-1\""), eco));

        HttpResponse<String> historialDeBruno = rest("GET", conversaciones() + "/" + idAna + "/mensajes", "bruno", null);
        HttpResponse<String> historialDeAna = rest("GET", conversaciones() + "/" + idBruno + "/mensajes", "ana", null);
        HttpResponse<String> deBruno = rest("GET", conversaciones(), "bruno", null);
        assertAll(
                () -> assertEquals(200, historialDeBruno.statusCode()),
                () -> assertTrue(historialDeBruno.body().contains("\"texto\":\"hola bruno\""), historialDeBruno.body()),
                () -> assertTrue(historialDeBruno.body().contains("\"leido\":false"), historialDeBruno.body()),
                () -> assertTrue(historialDeAna.body().contains("\"leido\":true"), "lo propio no queda pendiente"),
                () -> assertTrue(deBruno.body().contains("\"apodoOtro\":\"ana\""), deBruno.body()),
                () -> assertEquals(1, noLeidosCon(deBruno.body(), idAna), deBruno.body()),
                () -> assertTrue(VERIFICACIONES.stream().anyMatch(v -> v.contains("\"contexto\":\"MENSAJE_PRIVADO\""))
                        && VERIFICACIONES.stream().anyMatch(v -> v.contains("hola bruno")),
                        "la lista negra se consulta con contexto MENSAJE_PRIVADO"));

        assertEquals(204, rest("POST", conversaciones() + "/" + idAna + "/leido", "bruno", null).statusCode());
        assertEquals(0, noLeidosCon(rest("GET", conversaciones(), "bruno", null).body(), idAna));
    }

    @Test
    @DisplayName("C no lee la conversacion A-B por REST ni recibe sus mensajes, ni se cuela en la cola de B")
    void cNoLeeLoDeAB() throws Exception {
        StompSession carla = conectar("carla", new Registro());
        BlockingQueue<String> colaDeCarla = escucharSuCola("carla", carla);
        StompSession ana = conectar("ana", new Registro());

        escribir(ana, "bruno", "solo para bruno", "cli-2");

        assertNull(colaDeCarla.poll(2, TimeUnit.SECONDS), "a C no le llega nada de A-B");
        UUID idAna = JUGADORES.get("ana");
        UUID idBruno = JUGADORES.get("bruno");
        HttpResponse<String> conBruno = rest("GET", conversaciones() + "/" + idBruno + "/mensajes", "carla", null);
        HttpResponse<String> conAna = rest("GET", conversaciones() + "/" + idAna + "/mensajes", "carla", null);
        HttpResponse<String> deCarla = rest("GET", conversaciones(), "carla", null);
        HttpResponse<String> porClave = rest("GET", conversaciones() + "/dm:" + idAna + ":" + idBruno + "/mensajes",
                "carla", null);
        assertAll(
                () -> assertEquals("[]", conBruno.body(), "con el token de C es la conversacion C-B, vacia"),
                () -> assertEquals("[]", conAna.body()),
                () -> assertEquals("[]", deCarla.body()),
                () -> assertEquals(400, porClave.statusCode(), "la conversacion de otros no se puede nombrar"));

        Registro registro = new Registro();
        StompSession espia = conectar("carla", registro);
        espia.subscribe("/usuario/" + idBruno + "/cola/mensajes-directos", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders cabeceras) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders cabeceras, Object cuerpo) {
                registro.errores.add("MENSAJE INESPERADO");
            }
        });
        String rechazo = registro.errores.poll(10, TimeUnit.SECONDS);
        assertNotNull(rechazo);
        assertFalse(rechazo.contains("MENSAJE INESPERADO"), rechazo);
    }

    @Test
    @DisplayName("A no puede fingir ser C: el remitente sale del token aunque el cuerpo diga otra cosa")
    void nadieEscribeEnNombreDeOtro() throws Exception {
        StompSession bruno = conectar("bruno", new Registro());
        BlockingQueue<String> colaDeBruno = escucharSuCola("bruno", bruno);
        StompSession fede = conectar("fede", new Registro());
        UUID idCarla = JUGADORES.get("carla");
        UUID idFede = JUGADORES.get("fede");

        fede.send("/app/mensajes-directos/" + JUGADORES.get("bruno"),
                ("{\"texto\":\"soy carla, de verdad\",\"remitente\":\"" + idCarla + "\",\"apodoRemitente\":\"carla\"}")
                        .getBytes(StandardCharsets.UTF_8));

        String recibido = colaDeBruno.poll(10, TimeUnit.SECONDS);
        assertNotNull(recibido);
        assertAll(
                () -> assertTrue(recibido.contains("\"remitente\":\"" + idFede + "\""), recibido),
                () -> assertTrue(recibido.contains("\"apodoRemitente\":\"fede\""), recibido),
                () -> assertFalse(recibido.contains(idCarla.toString()), recibido));

        HttpResponse<String> porRest = rest("POST", conversaciones() + "/" + JUGADORES.get("bruno") + "/mensajes",
                "fede", "{\"texto\":\"otra vez\",\"remitente\":\"" + idCarla + "\"}");
        assertAll(
                () -> assertEquals(201, porRest.statusCode(), porRest.body()),
                () -> assertTrue(porRest.body().contains("\"remitente\":\"" + idFede + "\""), porRest.body()));
    }

    @Test
    @DisplayName("un texto con un termino de la lista negra no se entrega y el remitente recibe el motivo")
    void listaNegra() throws Exception {
        StompSession bruno = conectar("bruno", new Registro());
        BlockingQueue<String> colaDeBruno = escucharSuCola("bruno", bruno);
        StompSession gala = conectar("gala", new Registro());
        BlockingQueue<String> colaDeGala = escucharSuCola("gala", gala);

        escribir(gala, "bruno", "esto lleva " + TERMINO_VETADO, "cli-3");

        String rechazo = colaDeGala.poll(10, TimeUnit.SECONDS);
        assertNotNull(rechazo);
        assertAll(
                () -> assertTrue(rechazo.contains("\"tipo\":\"RECHAZO\""), rechazo),
                () -> assertTrue(rechazo.contains("\"motivo\":\"TEXTO_NO_PERMITIDO\""), rechazo),
                () -> assertTrue(rechazo.contains("\"idCliente\":\"cli-3\""), rechazo),
                () -> assertNull(colaDeBruno.poll(2, TimeUnit.SECONDS), "a B no le llega nada"),
                () -> assertEquals("[]", rest("GET", conversaciones() + "/" + JUGADORES.get("gala") + "/mensajes",
                        "bruno", null).body(), "y no queda en el historial"));
    }

    @Test
    @DisplayName("un sancionado no escribe, y a un uid que no existe no se le escribe")
    void sancionYDestinatario() throws Exception {
        StompSession dario = conectar("dario", new Registro());
        BlockingQueue<String> colaDeDario = escucharSuCola("dario", dario);

        escribir(dario, "bruno", "hola", "cli-4");
        String sancion = colaDeDario.poll(10, TimeUnit.SECONDS);
        assertNotNull(sancion);
        assertTrue(sancion.contains("\"motivo\":\"SANCIONADO\""), sancion);

        StompSession hugo = conectar("hugo", new Registro());
        BlockingQueue<String> colaDeHugo = escucharSuCola("hugo", hugo);
        escribir(hugo, "nadie-que-exista", "hola", "cli-5");
        String inexistente = colaDeHugo.poll(10, TimeUnit.SECONDS);
        assertNotNull(inexistente);
        assertTrue(inexistente.contains("\"motivo\":\"DESTINATARIO_INEXISTENTE\""), inexistente);
    }

    @Test
    @DisplayName("el limite de frecuencia: el sexto seguido es 429 con Retry-After, por las dos vias")
    void limiteDeFrecuencia() throws Exception {
        String bruno = conversaciones() + "/" + JUGADORES.get("bruno") + "/mensajes";
        for (int i = 1; i <= 5; i++) {
            HttpResponse<String> ok = rest("POST", bruno, "eva", "{\"texto\":\"mensaje " + i + "\"}");
            assertEquals(201, ok.statusCode(), ok.body());
        }

        HttpResponse<String> sexto = rest("POST", bruno, "eva", "{\"texto\":\"mensaje 6\"}");
        assertAll(
                () -> assertEquals(429, sexto.statusCode(), sexto.body()),
                () -> assertTrue(sexto.headers().firstValue("Retry-After").isPresent()),
                () -> assertTrue(sexto.body().contains("demasiados-mensajes"), sexto.body()));

        StompSession eva = conectar("eva", new Registro());
        BlockingQueue<String> colaDeEva = escucharSuCola("eva", eva);
        escribir(eva, "bruno", "por STOMP tampoco", "cli-6");
        String rechazo = colaDeEva.poll(10, TimeUnit.SECONDS);
        assertNotNull(rechazo);
        assertTrue(rechazo.contains("\"motivo\":\"DEMASIADO_RAPIDO\""), rechazo);
    }

    @Test
    @DisplayName("el respaldo REST entrega igual: B conectado lo recibe en vivo; y si no escucha, le llega un aviso")
    void respaldoRestYAviso() throws Exception {
        UUID idInes = JUGADORES.get("ines");
        UUID idHugo = JUGADORES.get("hugo");
        StompSession hugo = conectar("hugo", new Registro());
        BlockingQueue<String> colaDeHugo = escucharSuCola("hugo", hugo);

        HttpResponse<String> enviado = rest("POST", conversaciones() + "/" + idHugo + "/mensajes", "ines",
                "{\"texto\":\"te escribo por REST\",\"idCliente\":\"cli-7\"}");
        String recibido = colaDeHugo.poll(10, TimeUnit.SECONDS);
        assertAll(
                () -> assertEquals(201, enviado.statusCode(), enviado.body()),
                () -> assertTrue(enviado.body().contains("\"idCliente\":\"cli-7\""), enviado.body()),
                () -> assertNotNull(recibido, "el respaldo REST tambien entrega en vivo"),
                () -> assertTrue(recibido.contains("te escribo por REST"), recibido));

        HttpResponse<String> reintento = rest("POST", conversaciones() + "/" + idHugo + "/mensajes", "ines",
                "{\"texto\":\"te escribo por REST\",\"idCliente\":\"cli-7\"}");
        assertAll(
                () -> assertEquals(201, reintento.statusCode()),
                () -> assertEquals(enviado.body(), reintento.body(), "el reintento devuelve el mismo mensaje"),
                () -> assertNull(colaDeHugo.poll(1, TimeUnit.SECONDS), "y no se entrega dos veces"));

        // Julia no se conecta nunca: el mensaje la espera en el historial y a
        // su bandeja le llega UN aviso, sin el texto.
        String paraJulia = "\"usuarioId\":\"" + JUGADORES.get("julia") + "\"";
        String aJulia = conversaciones() + "/" + JUGADORES.get("julia") + "/mensajes";
        assertEquals(201, rest("POST", aJulia, "ines", "{\"texto\":\"secreto para julia\"}").statusCode());
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (avisosPara(paraJulia).isEmpty() && System.nanoTime() < limite) {
            Thread.sleep(50);
        }
        List<String> avisos = avisosPara(paraJulia);
        assertEquals(1, avisos.size(), "tiene que llegar un aviso a la bandeja");
        String aviso = avisos.get(0);
        assertAll(
                () -> assertTrue(aviso.contains("\"tipo\":\"MENSAJE_PRIVADO\""), aviso),
                () -> assertTrue(aviso.contains("ines te escribi"), aviso),
                () -> assertFalse(aviso.contains("secreto para julia"), "el aviso no lleva el texto"));

        // El segundo vuelve a avisar, con el MISMO id (el del primer no leido):
        // notificaciones lo descarta con 409 y a la bandeja llega uno por racha.
        assertEquals(201, rest("POST", aJulia, "ines", "{\"texto\":\"otro\"}").statusCode());
        long hasta = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (intentosPara(paraJulia).size() < 2 && System.nanoTime() < hasta) {
            Thread.sleep(50);
        }
        List<String> intentos = intentosPara(paraJulia);
        assertAll(
                () -> assertEquals(2, intentos.size(), "cada mensaje sin escuchar intenta el aviso"),
                () -> assertEquals(campo(intentos.get(0), "id"), campo(intentos.get(1), "id"),
                        "los dos llevan el id de la racha"),
                () -> assertEquals(1, avisosPara(paraJulia).size(), "un aviso por racha de no leidos"));
    }

    private static List<String> avisosPara(String destinatario) {
        return AVISOS.stream().filter(aviso -> aviso.contains(destinatario)).toList();
    }

    private static List<String> intentosPara(String destinatario) {
        return INTENTOS_DE_AVISO.stream().filter(aviso -> aviso.contains(destinatario)).toList();
    }

    /** Un campo de texto de un JSON plano, sin parsear entero. */
    private static String campo(String json, String nombre) {
        Matcher m = Pattern.compile("\"" + nombre + "\":\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }
}
