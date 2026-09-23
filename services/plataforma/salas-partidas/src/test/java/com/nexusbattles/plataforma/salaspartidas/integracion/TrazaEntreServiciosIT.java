package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.HeroeDelJugador;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDelHeroe;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La traza sobrevive al salto entre servicios — regla 5 de plataforma.
 *
 * <h2>Por que hace falta esta prueba y no bastan las unitarias</h2>
 *
 * {@code FiltroDeTrazaTest} demuestra que la entrada funciona e
 * {@code InterceptorDeTrazaTest} que la salida tambien. Ninguna de las dos
 * demuestra lo unico que la regla 5 promete: que <b>la misma traza</b> entra
 * por el borde y sale hacia el siguiente servicio. Entre las dos mitades hay
 * un montaje —la autoconfiguracion, el orden de los filtros, el MDC vivo
 * durante la llamada saliente, el cliente HTTP concreto de este servicio— que
 * solo se comprueba con el servicio arrancado.
 *
 * <p>Y ese montaje ya fallo una vez en silencio: hasta R11 el
 * {@code traceparent} entraba, se guardaba en el MDC y <b>moria ahi</b>,
 * porque ningun cliente HTTP lo reenviaba. Las dos mitades eran correctas por
 * separado y la promesa estaba incumplida.
 *
 * <h2>El salto que se recorre</h2>
 *
 * <pre>
 *   peticion con traceparent
 *     -> salas-partidas (POST /api/v1/salas)
 *     -> puerta de sancion
 *     -> HTTP real hacia el doble de moderacion-sanciones
 * </pre>
 *
 * <p>El doble es un servidor HTTP de verdad que <b>anota las cabeceras que
 * recibe</b>; el camino hasta el es el de produccion: filtro de traza,
 * cadena de seguridad, caso de uso, {@code RestClient} con su interceptor.
 *
 * <p>La puerta de heroe se dobla con un simulacro a proposito: su cliente
 * pagina el inventario y ademas consulta productos y heroes, asi que fingirlo
 * por HTTP anadiria tres dobles mas sin anadir ni un salto distinto al que ya
 * se esta midiendo.
 *
 * <p>Lo que se afirma es exactamente lo que define W3C Trace Context, ni mas
 * ni menos: <b>mismo trace id, span nuevo por salto</b>. En este proyecto no
 * hay agregador de trazas ni backend de tracing; lo que existe es correlacion
 * por identificador, y es lo que se prueba.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Regla 5: la misma traza entra por el borde y sale hacia el siguiente servicio")
class TrazaEntreServiciosIT {

    /** Trace id valido de W3C: 32 hexadecimales y no todo ceros. */
    private static final String TRAZA = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_DE_ENTRADA = "1111111111111111";

    private static final int PUERTO_DOBLE = puertoLibre();

    /** Cada `traceparent` que el doble vio llegar, en orden. */
    private static final List<String> RECIBIDAS = new CopyOnWriteArrayList<>();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void dependencias(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
        registro.add("salas.sanciones.url", () -> "http://localhost:" + PUERTO_DOBLE + "/api/v1");
    }

    /** Ver el javadoc de la clase: el inventario no aporta otro salto. */
    @MockitoBean
    private HeroeDelJugador heroes;

    @LocalServerPort
    private int puerto;

    private HttpServer doble;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void levantarElDoble() throws IOException {
        RECIBIDAS.clear();

        Mockito.when(heroes.consultar(ArgumentMatchers.any())).thenReturn(
                EstadoDelHeroe.disponible(
                        new HeroeDeCombate("h-1", "Sombra de Vael", "guerrero", null, 7, 140, 140, 10)));

        doble = HttpServer.create(new InetSocketAddress(PUERTO_DOBLE), 0);
        doble.createContext("/api/v1/sanciones", intercambio -> {
            String traceparent = intercambio.getRequestHeaders().getFirst(FiltroDeTraza.CABECERA);
            RECIBIDAS.add(traceparent == null ? "" : traceparent);
            responder(intercambio, """
                    {"sancionActiva": false, "motivo": null, "vigenteHasta": null, "tipo": null}""");
        });
        doble.start();
    }

    @AfterEach
    void apagarElDoble() {
        if (doble != null) {
            doble.stop(0);
        }
    }

    private static void responder(HttpExchange intercambio, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(200, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }

    private static int puertoLibre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException imposible) {
            throw new IllegalStateException("No hay puerto libre para el doble", imposible);
        }
    }

    /** Las cuatro partes de un `traceparent`: version, traza, span y banderas. */
    private static String[] partes(String traceparent) {
        assertNotNull(traceparent, "no llego ningun traceparent");
        assertTrue(!traceparent.isBlank(), "llego un traceparent vacio");
        String[] trozos = traceparent.split("-");
        assertEquals(4, trozos.length, "traceparent mal formado: " + traceparent);
        return trozos;
    }

    private HttpResponse<String> crearSala(String traceparentEntrante, String apodo) throws Exception {
        String token = EmisorDeTokensDePrueba.emisor().tokenDeJugador(apodo, UUID.randomUUID());
        HttpRequest.Builder peticion = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + puerto + "/api/v1/salas"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"maximoParticipantes": 2, "modalidad": "UNO_CONTRA_UNO",
                         "recompensaCreditos": 0, "privada": false}"""));
        if (traceparentEntrante != null) {
            peticion.header(FiltroDeTraza.CABECERA, traceparentEntrante);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("el traceparent que entra sale hacia el siguiente servicio, con span propio")
    void laTrazaAtraviesaElSalto() throws Exception {
        HttpResponse<String> respuesta =
                crearSala("00-" + TRAZA + "-" + SPAN_DE_ENTRADA + "-01", "Bea");

        assertEquals(201, respuesta.statusCode(),
                "la sala tenia que crearse; si no, el salto no llego a ocurrir: " + respuesta.body());
        assertEquals(1, RECIBIDAS.size(), "el doble tenia que recibir exactamente una llamada");

        String[] salto = partes(RECIBIDAS.get(0));

        assertAll("la traza cruza el salto",
                () -> assertEquals("00", salto[0], "version de W3C"),
                () -> assertEquals(TRAZA, salto[1],
                        "el siguiente servicio tenia que recibir LA MISMA traza que entro"),

                // Span nuevo por salto: es lo que convierte una lista de mensajes
                // sueltos en un arbol. Reenviar el span del padre dejaria la
                // traza plana y no se sabria quien llamo a quien.
                () -> assertNotEquals(SPAN_DE_ENTRADA, salto[2], "cada salto abre su propio span"),
                () -> assertTrue(salto[2].matches("[0-9a-f]{16}"),
                        "el span es de 8 bytes en hexadecimal, como manda W3C"));
    }

    @Test
    @DisplayName("sin traceparent entrante, el servicio abre uno y lo propaga igual")
    void sinTrazaEntranteSeAbreUna() throws Exception {
        // La regla 5 no dice «reenvia la que venga»: dice que toda llamada
        // entre servicios lleva trace id. Si la peticion entra sin traza —una
        // sonda, un cliente que no la pone— se abre una.
        HttpResponse<String> respuesta = crearSala(null, "Cid");

        assertEquals(201, respuesta.statusCode(), respuesta.body());
        String[] salto = partes(RECIBIDAS.get(0));

        assertAll("se abre una traza nueva y valida",
                () -> assertTrue(salto[1].matches("[0-9a-f]{32}"), "32 hexadecimales"),
                () -> assertNotEquals("00000000000000000000000000000000", salto[1],
                        "no puede ser la traza nula que W3C prohibe"));
    }

    @Test
    @DisplayName("la respuesta devuelve el traceparent: quien llamo lo anota sin adivinarlo")
    void laRespuestaLlevaLaTraza() throws Exception {
        HttpResponse<String> respuesta =
                crearSala("00-" + TRAZA + "-" + SPAN_DE_ENTRADA + "-01", "Dan");

        String devuelta = respuesta.headers().firstValue(FiltroDeTraza.CABECERA).orElse(null);
        assertEquals(TRAZA, partes(devuelta)[1],
                "la respuesta lleva la traza para que el cliente la anote en SU bitacora");
    }
}
