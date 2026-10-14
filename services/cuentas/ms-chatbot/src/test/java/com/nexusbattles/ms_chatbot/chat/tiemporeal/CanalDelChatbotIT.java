package com.nexusbattles.ms_chatbot.chat.tiemporeal;

import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.ms_chatbot.chat.moderacion.ContenidoBloqueado;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;

/**
 * 7.4.9 — el canal en tiempo real del chatbot de punta a punta
 * ({@code contracts/websocket/chatbot.yaml} 1.0.0): la aplicacion entera en un
 * puerto de verdad, PostgreSQL real (Flyway V1–V12 con la base de V11), un
 * cliente STOMP sobre WebSocket ({@link WebSocketStompClient}) en
 * {@code /api/v1/ws-chatbot} y tokens RS256 firmados de verdad con su JWKS
 * ({@link EmisorDeTokensDePrueba}): el CONNECT pasa por el mismo decodificador
 * que la API.
 *
 * <p>Lo que se demuestra: la misma identidad que la API (jugador, visitante,
 * token roto que degrada, conexion sin identidad rechazada), la misma
 * conversacion y la misma forma de {@code Mensaje} que {@code POST
 * /chat/mensajes}, los mismos errores con su {@code motivo}, que nadie recibe
 * la cola de otro (ni la respuesta ni el aviso de soporte) y que el
 * interceptor rechaza todo destino que el contrato no declara.
 *
 * <p>Sin esperas a ojo: una suscripcion se da por viva cuando le llega una
 * sonda publicada por el servidor a ese mismo destino.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    // Bajo, para ver el 429 sin mandar veinte mensajes.
    "chatbot.limite.mensajes-por-minuto=4"
})
@DisplayName("Chatbot · canal en tiempo real STOMP (7.4.9, chatbot.yaml 1.0.0)")
class CanalDelChatbotIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    private static final String SONDA = "sonda-de-suscripcion";
    private static final String PREGUNTA = "¿Cómo publicar un producto en subasta?";
    private static final long ESPERA_S = 15;
    private static final ThreadPoolTaskScheduler PLANIFICADOR = new ThreadPoolTaskScheduler();

    static {
        PLANIFICADOR.setThreadNamePrefix("cliente-stomp-");
        PLANIFICADOR.initialize();
    }

    // La lista negra es otro servicio: aqui aprueba todo salvo lo que la prueba marque.
    @MockitoBean
    private ModeracionDeContenido moderacion;

    @Autowired
    private SimpMessagingTemplate mensajeria;

    @LocalServerPort
    private int puerto;

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();
    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<StompSession> abiertas = new ArrayList<>();

    @AfterEach
    void cerrar() {
        for (StompSession sesion : abiertas) {
            try {
                sesion.disconnect();
            } catch (RuntimeException yaCerrada) {
                // El servidor ya la cerro (las pruebas del interceptor lo provocan).
            }
        }
    }

    // ---------------------------------------------------------------- cliente

    /** Lo que llego a un destino: cabeceras STOMP y cuerpo JSON. */
    private record Recibido(StompHeaders cabeceras, JsonNode cuerpo) {
    }

    /** Una conexion de prueba con una cola por destino y otra para los ERROR del servidor. */
    private final class Cliente extends StompSessionHandlerAdapter {
        final BlockingQueue<String> fallos = new LinkedBlockingQueue<>();
        final Map<String, BlockingQueue<Recibido>> colas = Map.of(
            "/user/queue/chat/estado", new LinkedBlockingQueue<>(),
            "/user/queue/chat/respuestas", new LinkedBlockingQueue<>(),
            "/user/queue/chat/errores", new LinkedBlockingQueue<>(),
            "/user/queue/soporte/tickets", new LinkedBlockingQueue<>());
        private final BlockingQueue<String> sondas = new LinkedBlockingQueue<>();
        // El futuro de connectAsync se completa ANTES de afterConnected.
        private final CountDownLatch conectado = new CountDownLatch(1);
        StompSession sesion;
        volatile String usuario;
        volatile String latido;

        @Override
        public void afterConnected(StompSession sesion, StompHeaders cabecerasConnected) {
            this.usuario = cabecerasConnected.getFirst("user-name");
            this.latido = cabecerasConnected.getFirst("heart-beat");
            conectado.countDown();
        }

        @Override
        public void handleFrame(StompHeaders cabeceras, Object cuerpo) {
            fallos.add("ERROR: " + cabeceras.getFirst("message"));
        }

        @Override
        public void handleException(StompSession sesion, StompCommand comando, StompHeaders cabeceras, byte[] cuerpo,
                                    Throwable ex) {
            fallos.add("EXCEPCION: " + ex.getMessage());
        }

        @Override
        public void handleTransportError(StompSession sesion, Throwable ex) {
            fallos.add("TRANSPORTE: " + ex.getMessage());
        }

        BlockingQueue<Recibido> cola(String destino) {
            return colas.get(destino);
        }

        void suscribir(String destino, BlockingQueue<Recibido> cola) {
            sesion.subscribe(destino, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders cabeceras) {
                    return byte[].class;
                }

                @Override
                public void handleFrame(StompHeaders cabeceras, Object cuerpo) {
                    String texto = new String((byte[]) cuerpo, StandardCharsets.UTF_8);
                    if (texto.contains(SONDA)) {
                        sondas.add(texto);
                    } else {
                        cola.add(new Recibido(cabeceras, json.readTree(texto)));
                    }
                }
            });
        }

        /** Se suscribe a los cuatro destinos y no vuelve hasta que cada uno recibe su sonda. */
        Cliente suscritoATodo() throws Exception {
            for (Map.Entry<String, BlockingQueue<Recibido>> destino : colas.entrySet()) {
                suscribir(destino.getKey(), destino.getValue());
                String deUsuario = destino.getKey().substring("/user".length());
                boolean viva = false;
                long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(ESPERA_S);
                while (!viva && System.nanoTime() < limite) {
                    mensajeria.convertAndSendToUser(usuario, deUsuario, SONDA);
                    viva = sondas.poll(200, TimeUnit.MILLISECONDS) != null;
                }
                assertThat(viva).as("la suscripcion a %s nunca quedo activa", destino.getKey()).isTrue();
                while (sondas.poll(100, TimeUnit.MILLISECONDS) != null) {
                    // vaciando las sondas en vuelo
                }
            }
            return this;
        }

        void preguntar(String cuerpoJson) {
            StompHeaders cabeceras = new StompHeaders();
            cabeceras.setDestination("/app/chat/mensajes");
            cabeceras.setContentType(MimeTypeUtils.APPLICATION_JSON);
            sesion.send(cabeceras, cuerpoJson.getBytes(StandardCharsets.UTF_8));
        }

        Recibido siguiente(String destino) throws InterruptedException {
            Recibido recibido = cola(destino).poll(ESPERA_S, TimeUnit.SECONDS);
            assertThat(recibido).as("no llego nada a %s; errores del canal: %s", destino, fallos).isNotNull();
            return recibido;
        }
    }

    private Cliente conectar(Map<String, String> connect, WebSocketHttpHeaders handshake) throws Exception {
        Cliente cliente = new Cliente();
        StompHeaders cabeceras = new StompHeaders();
        connect.forEach(cabeceras::add);
        if (!connect.containsKey("heart-beat")) {
            // Sin planificador, este cliente no manda latidos: que no los prometa.
            cabeceras.setHeartbeat(new long[] {0, 0});
        }
        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        // Para vigilar los latidos que pida la prueba (sin el, el cliente no puede).
        stomp.setTaskScheduler(PLANIFICADOR);
        cliente.sesion = stomp
            .connectAsync("ws://localhost:" + puerto + "/api/v1/ws-chatbot", handshake, cabeceras, cliente)
            .get(ESPERA_S, TimeUnit.SECONDS);
        abiertas.add(cliente.sesion);
        assertThat(cliente.conectado.await(ESPERA_S, TimeUnit.SECONDS)).as("CONNECTED").isTrue();
        return cliente;
    }

    private Cliente conectar(Map<String, String> connect) throws Exception {
        return conectar(connect, new WebSocketHttpHeaders());
    }

    private static Map<String, String> jugador(String token) {
        return Map.of("Authorization", "Bearer " + token);
    }

    private static Map<String, String> visitante(String sesion) {
        return Map.of("X-Id-Sesion-Anonima", sesion);
    }

    private static String sesionDeclarada() {
        return "visitante-" + UUID.randomUUID();
    }

    private String token(UUID uid) {
        return emisor.tokenDeJugador("jugador_" + uid.toString().substring(0, 6), uid);
    }

    private HttpResponse<String> llamar(String metodo, String ruta, Map<String, String> cabeceras, String cuerpo)
        throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1" + ruta))
            .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo))
            .header("Content-Type", "application/json");
        cabeceras.forEach(peticion::header);
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode ultimoDelHistorial(Map<String, String> identidad) throws Exception {
        HttpResponse<String> historial = llamar("GET", "/chat/historial", identidad, null);
        assertThat(historial.statusCode()).isEqualTo(200);
        JsonNode mensajes = json.readTree(historial.body());
        return mensajes.get(mensajes.size() - 1);
    }

    /**
     * El mismo objeto JSON. Las fechas, como instantes a menos de 1 ms: la
     * respuesta sale de la entidad recien guardada (nanosegundos de
     * {@code Instant.now()}) y la API la relee de PostgreSQL (microsegundos),
     * igual que hoy {@code POST /chat/mensajes} frente a {@code GET /chat/historial}.
     */
    private static void mismoObjeto(JsonNode real, JsonNode esperado) {
        assertThat(real.propertyNames()).containsExactlyInAnyOrderElementsOf(esperado.propertyNames());
        for (String campo : esperado.propertyNames()) {
            JsonNode a = real.get(campo);
            JsonNode b = esperado.get(campo);
            if (FECHAS.contains(campo) && a.isString() && b.isString()) {
                Duration diferencia = Duration.between(Instant.parse(a.stringValue()), Instant.parse(b.stringValue()));
                assertThat(diferencia.abs()).as(campo).isLessThan(Duration.ofMillis(1));
            } else {
                assertThat(a).as(campo).isEqualTo(b);
            }
        }
    }

    private static final List<String> FECHAS = List.of("fechaEnvio", "creadoEn", "actualizadoEn");

    // ------------------------------------------------------------------ pruebas

    @Test
    @DisplayName("un jugador pregunta por el canal: ESCRIBIENDO y despues el mismo Mensaje que guarda y devuelve la API")
    void unJugadorRecibeEscribiendoYElMismoMensajeQueLaApi() throws Exception {
        UUID uid = UUID.randomUUID();
        Map<String, String> identidad = jugador(token(uid));
        Cliente ana = conectar(identidad).suscritoATodo();
        assertThat(ana.usuario).isEqualTo(uid.toString());

        ana.preguntar("{\"contenido\":\"" + PREGUNTA + "\",\"vista\":\"SUBASTAS\",\"idCorrelacion\":\"p-1\"}");

        Recibido estado = ana.siguiente("/user/queue/chat/estado");
        assertThat(estado.cuerpo().get("estado").stringValue()).isEqualTo("ESCRIBIENDO");
        assertThat(estado.cuerpo().get("idCorrelacion").stringValue()).isEqualTo("p-1");
        assertThat(estado.cabeceras().getFirst("id-correlacion")).isEqualTo("p-1");

        Recibido respuesta = ana.siguiente("/user/queue/chat/respuestas");
        assertThat(respuesta.cabeceras().getFirst("id-correlacion")).isEqualTo("p-1");
        assertThat(respuesta.cabeceras().getFirst("traceparent")).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
        JsonNode mensaje = respuesta.cuerpo();
        assertThat(mensaje.get("remitente").stringValue()).isEqualTo("BOT");
        assertThat(mensaje.get("contenido").stringValue()).isNotBlank();
        assertThat(mensaje.has("idCorrelacion")).as("el cuerpo es el Mensaje de la API, sin nada mas").isFalse();

        // La misma conversacion: la API devuelve, campo por campo, lo que llego por el canal.
        mismoObjeto(mensaje, ultimoDelHistorial(identidad));
        assertThat(ana.cola("/user/queue/chat/errores")).isEmpty();
    }

    @Test
    @DisplayName("la respuesta de un jugador no le llega a otro conectado a la vez")
    void laRespuestaDeUnoNoLeLlegaAOtro() throws Exception {
        Cliente ana = conectar(jugador(token(UUID.randomUUID()))).suscritoATodo();
        Cliente beto = conectar(jugador(token(UUID.randomUUID()))).suscritoATodo();
        Cliente visita = conectar(visitante(sesionDeclarada())).suscritoATodo();

        ana.preguntar("{\"contenido\":\"" + PREGUNTA + "\",\"idCorrelacion\":\"solo-ana\"}");
        ana.siguiente("/user/queue/chat/respuestas");

        for (Cliente otro : List.of(beto, visita)) {
            for (String destino : otro.colas.keySet()) {
                assertThat(otro.cola(destino).poll(500, TimeUnit.MILLISECONDS))
                    .as("a %s no le llega nada de ana en %s", otro.usuario, destino).isNull();
            }
        }
    }

    @Test
    @DisplayName("un visitante conversa con la sesion que declara el navegador y la API ve la misma conversacion")
    void unVisitanteConversaConSuSesionDeclarada() throws Exception {
        String sesion = sesionDeclarada();
        Cliente visita = conectar(visitante(sesion)).suscritoATodo();
        assertThat(visita.usuario).startsWith("anonimo:").doesNotContain(sesion);

        visita.preguntar("{\"contenido\":\"hola\",\"idCorrelacion\":\"v-1\"}");
        JsonNode respuesta = visita.siguiente("/user/queue/chat/respuestas").cuerpo();

        mismoObjeto(respuesta, ultimoDelHistorial(visitante(sesion)));
        // Otra sesion de visitante no ve esa conversacion.
        HttpResponse<String> ajena = llamar("GET", "/chat/historial", visitante(sesionDeclarada()), null);
        assertThat(json.readTree(ajena.body()).size()).isZero();
    }

    @Test
    @DisplayName("un token roto o vencido degrada a visitante, como en la API; sin identidad no hay conexion")
    void laIdentidadDelConnectSigueLasReglasDeLaApi() throws Exception {
        UUID uid = UUID.randomUUID();
        String sesion = sesionDeclarada();

        Cliente vencido = conectar(Map.of("Authorization", "Bearer " + emisor.tokenCaducado("lyra", uid),
            "X-Id-Sesion-Anonima", sesion)).suscritoATodo();
        assertThat(vencido.usuario).startsWith("anonimo:");
        vencido.preguntar("{\"contenido\":\"hola\"}");
        JsonNode respuesta = vencido.siguiente("/user/queue/chat/respuestas").cuerpo();
        // Quedo en la conversacion del visitante...
        mismoObjeto(respuesta, ultimoDelHistorial(visitante(sesion)));
        assertThat(json.readTree(llamar("GET", "/chat/historial", jugador(token(uid)), null).body()).size())
            .as("y no en la del jugador del token vencido").isZero();

        Cliente ajeno = conectar(Map.of("Authorization", "Bearer " + emisor.tokenFirmadoPorOtro("lyra", uid),
            "X-Id-Sesion-Anonima", sesionDeclarada()));
        assertThat(ajeno.usuario).startsWith("anonimo:");

        Cliente deServicio = conectar(Map.of("Authorization", "Bearer " + emisor.tokenDeServicio("ms-subastas"),
            "X-Id-Sesion-Anonima", sesionDeclarada()));
        assertThat(deServicio.usuario).as("un token sin uid no es un jugador").startsWith("anonimo:");

        assertThatThrownBy(() -> conectar(Map.of())).as("sin token ni sesion").isInstanceOf(Exception.class);
        assertThatThrownBy(() -> conectar(Map.of("Authorization", "Bearer esto-no-es-un-jwt")))
            .as("un token roto sin sesion de visitante").isInstanceOf(Exception.class);
        assertThatThrownBy(() -> conectar(visitante(uid.toString())))
            .as("un uid no es una sesion de visitante").isInstanceOf(Exception.class);
    }

    /**
     * Un rechazo del interceptor: la conexion se cierra SIEMPRE, y lo primero
     * que ve el cliente es el frame ERROR con el motivo (lo que devuelve) o,
     * por una carrera conocida de Spring, solo el cierre (devuelve null).
     *
     * <p>La carrera: tras el ERROR, {@code StompSubProtocolHandler} cierra la
     * sesion en el acto. Si otro hilo todavia tiene el cerrojo de envio de esa
     * sesion ({@code ConcurrentWebSocketSessionDecorator}; aqui, el que acaba de
     * mandar CONNECTED y espera a que el contenedor confirme la escritura), el
     * ERROR se queda en el bufer y el cierre gana. Con la maquina cargada paso
     * en esta prueba. Por eso cada intruso espera un momento tras conectar (lo
     * normal es que el motivo llegue), y lo que se exige siempre es lo que
     * importa: el frame no pasa y la conexion se cierra.
     */
    private static String rechazo(Cliente cliente, String que) throws InterruptedException {
        String primero = cliente.fallos.poll(ESPERA_S, TimeUnit.SECONDS);
        assertThat(primero).as("%s: ni ERROR ni cierre", que).isNotNull();
        assertThat(primero.startsWith("ERROR: ") || primero.equals("TRANSPORTE: Connection closed"))
            .as("%s: %s", que, primero).isTrue();
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(ESPERA_S);
        while (cliente.sesion.isConnected() && System.nanoTime() < limite) {
            Thread.sleep(20);
        }
        assertThat(cliente.sesion.isConnected()).as("%s: la conexion se cierra", que).isFalse();
        return primero.startsWith("ERROR: ") ? primero.substring("ERROR: ".length()) : null;
    }

    /** Un intruso conectado, con CONNECTED ya enviado del todo (ver {@link #rechazo}). */
    private Cliente intruso() throws Exception {
        Cliente cliente = conectar(visitante(sesionDeclarada()));
        Thread.sleep(150);
        return cliente;
    }

    @Test
    @DisplayName("nadie se suscribe a la cola de otro ni a un destino que el contrato no declara")
    void elInterceptorSoloAdmiteLosDestinosDelContrato() throws Exception {
        UUID uid = UUID.randomUUID();
        List<String> motivos = new ArrayList<>();
        for (String destino : List.of("/user/" + uid + "/queue/chat/respuestas", "/queue/chat/respuestas",
            "/topic/chat", "/user/queue/chat/**", "/user/queue/otra")) {
            Cliente intruso = intruso();
            intruso.suscribir(destino, new LinkedBlockingQueue<>());
            String motivo = rechazo(intruso, "SUBSCRIBE a " + destino);
            if (motivo != null) {
                assertThat(motivo).as("SUBSCRIBE a %s", destino)
                    .isEqualTo("Ese destino no existe en el canal del chatbot.");
                motivos.add(motivo);
            }
        }

        Cliente otro = intruso();
        StompHeaders cabeceras = new StompHeaders();
        cabeceras.setDestination("/app/chat/otra-cosa");
        otro.sesion.send(cabeceras, "{}".getBytes(StandardCharsets.UTF_8));
        String motivo = rechazo(otro, "SEND a otro destino");
        if (motivo != null) {
            assertThat(motivo).startsWith("El canal del chatbot solo acepta preguntas en /app/chat/mensajes");
            motivos.add(motivo);
        }
        assertThat(motivos).as("el motivo de la politica llega al cliente (salvo la carrera de arriba)")
            .isNotEmpty();
    }

    @Test
    @DisplayName("los errores vuelven por la cola de errores con el estado y el motivo de la API")
    void losErroresVuelvenConElMotivoDeLaApi() throws Exception {
        doThrow(new ContenidoBloqueado())
            .when(moderacion).verificar(contains("palabrota"));
        Cliente visita = conectar(visitante(sesionDeclarada())).suscritoATodo();

        visita.preguntar("{\"contenido\":\"eres una palabrota\",\"idCorrelacion\":\"e-1\"}");
        JsonNode bloqueado = visita.siguiente("/user/queue/chat/errores").cuerpo();
        assertThat(bloqueado.get("status").intValue()).isEqualTo(422);
        assertThat(bloqueado.get("motivo").stringValue()).isEqualTo("CONTENIDO_BLOQUEADO");
        assertThat(bloqueado.get("idCorrelacion").stringValue()).isEqualTo("e-1");
        assertThat(bloqueado.get("instance").stringValue()).isEqualTo("/app/chat/mensajes");

        visita.preguntar("{\"contenido\":\"<script>alert(1)</script>\",\"idCorrelacion\":\"e-2\"}");
        JsonNode inyeccion = visita.siguiente("/user/queue/chat/errores").cuerpo();
        assertThat(inyeccion.get("status").intValue()).isEqualTo(422);
        assertThat(inyeccion.get("motivo").stringValue()).isEqualTo("INYECCION_DETECTADA");

        visita.preguntar("{\"contenido\":\"   \",\"idCorrelacion\":\"e-3\"}");
        JsonNode vacio = visita.siguiente("/user/queue/chat/errores").cuerpo();
        assertThat(vacio.get("status").intValue()).isEqualTo(400);
        assertThat(vacio.get("idCorrelacion").stringValue()).isEqualTo("e-3");

        visita.preguntar("esto no es json");
        assertThat(visita.siguiente("/user/queue/chat/errores").cuerpo().get("status").intValue()).isEqualTo(400);

        // La conexion sigue abierta y la siguiente pregunta se responde.
        visita.preguntar("{\"contenido\":\"hola\",\"idCorrelacion\":\"e-4\"}");
        assertThat(visita.siguiente("/user/queue/chat/respuestas").cabeceras().getFirst("id-correlacion"))
            .isEqualTo("e-4");
        assertThat(visita.fallos).isEmpty();
    }

    @Test
    @DisplayName("el limite de mensajes por minuto de la API vale tambien por el canal: 429 con reintentarEn")
    void elLimiteDeFrecuenciaEsElDeLaApi() throws Exception {
        Cliente visita = conectar(visitante(sesionDeclarada())).suscritoATodo();
        // 4 por minuto (propiedad de la prueba), de una en una como la ventana.
        for (int i = 1; i <= 4; i++) {
            visita.preguntar("{\"contenido\":\"hola " + i + "\",\"idCorrelacion\":\"l-" + i + "\"}");
            visita.siguiente("/user/queue/chat/respuestas");
        }
        visita.preguntar("{\"contenido\":\"hola 5\",\"idCorrelacion\":\"l-5\"}");

        JsonNode limite = visita.siguiente("/user/queue/chat/errores").cuerpo();
        assertThat(limite.get("status").intValue()).isEqualTo(429);
        assertThat(limite.get("motivo").stringValue()).isEqualTo("LIMITE_DE_FRECUENCIA");
        assertThat(limite.get("reintentarEn").longValue()).isBetween(1L, 60L);
        assertThat(limite.get("idCorrelacion").stringValue()).isEqualTo("l-5");
    }

    @Test
    @DisplayName("cuando un administrador atiende una solicitud, solo su dueno la recibe, con lo que ve en GET /chat/tickets")
    void soloElDuenoRecibeElCambioDeSuSolicitud() throws Exception {
        UUID uid = UUID.randomUUID();
        Map<String, String> dueno = jugador(token(uid));
        HttpResponse<String> abierto = llamar("POST", "/chat/tickets", dueno,
            "{\"categoria\":\"SOPORTE_TECNICO\",\"asunto\":\"No carga\",\"mensaje\":\"Mi inventario no carga.\"}");
        assertThat(abierto.statusCode()).isEqualTo(201);
        String ticketId = json.readTree(abierto.body()).get("ticketId").stringValue();

        Cliente ana = conectar(dueno).suscritoATodo();
        Cliente otraPestana = conectar(dueno).suscritoATodo();
        Cliente beto = conectar(jugador(token(UUID.randomUUID()))).suscritoATodo();

        String administrador = emisor.tokenDeUsuario("admin", UUID.randomUUID(), "ADMINISTRADOR");
        HttpResponse<String> atendido = llamar("PATCH", "/chatbot/admin/tickets/" + ticketId,
            Map.of("Authorization", "Bearer " + administrador),
            "{\"estado\":\"RESUELTO\",\"respuesta\":\"Ya quedo: vuelve a entrar.\"}");
        assertThat(atendido.statusCode()).isEqualTo(200);

        JsonNode aviso = ana.siguiente("/user/queue/soporte/tickets").cuerpo();
        assertThat(aviso.get("ticketId").stringValue()).isEqualTo(ticketId);
        assertThat(aviso.get("estado").stringValue()).isEqualTo("RESUELTO");
        assertThat(aviso.get("respuesta").stringValue()).isEqualTo("Ya quedo: vuelve a entrar.");
        assertThat(aviso.has("uid") || aviso.has("contexto") || aviso.has("asignadoA"))
            .as("solo lo que ve el jugador").isFalse();
        JsonNode suyos = json.readTree(llamar("GET", "/chat/tickets", dueno, null).body());
        mismoObjeto(aviso, suyos.get(0));

        assertThat(otraPestana.siguiente("/user/queue/soporte/tickets").cuerpo()).isEqualTo(aviso);
        assertThat(beto.cola("/user/queue/soporte/tickets").poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    @DisplayName("el handshake solo admite los origenes de la API, y el servidor ofrece latidos de 10 s")
    void origenesYLatidos() throws Exception {
        WebSocketHttpHeaders intruso = new WebSocketHttpHeaders();
        intruso.setOrigin("http://intruso.example");
        assertThatThrownBy(() -> conectar(visitante(sesionDeclarada()), intruso)).isInstanceOf(Exception.class);

        WebSocketHttpHeaders propio = new WebSocketHttpHeaders();
        propio.setOrigin("http://localhost:8080");
        Map<String, String> pideLatidos = new HashMap<>(visitante(sesionDeclarada()));
        pideLatidos.put("heart-beat", "0,10000");
        Cliente conLatidos = conectar(pideLatidos, propio);
        // El CONNECTED dice lo que ofrece el servidor; cada lado calcula el
        // intervalo efectivo con lo que pidio el otro (aqui: 10 s del servidor
        // al cliente, nada del cliente al servidor).
        assertThat(conLatidos.latido).isEqualTo("10000,10000");
    }
}
