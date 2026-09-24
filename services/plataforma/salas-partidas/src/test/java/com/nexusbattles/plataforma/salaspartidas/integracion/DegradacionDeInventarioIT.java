package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesDelJugador;
import com.nexusbattles.plataforma.resiliencia.ErroresDeDegradacion;
import com.nexusbattles.plataforma.resiliencia.RegistroDeDegradacion;
import com.nexusbattles.plataforma.salaspartidas.configuracion.ConfiguracionDeResiliencia;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HU-DIS-003 de punta a punta dentro del servicio: el inventario se cae, la
 * seccion que depende de el se degrada con el aviso correcto, el resto sigue
 * sirviendo, y cuando vuelve el circuito se cierra solo.
 *
 * <p>Es el ejemplo literal de la historia —«el servicio de inventario del
 * equipo socio se cae... el jugador ve el aviso "Inventario no disponible
 * temporalmente" en lugar de una pantalla en blanco»— recorrido con la cadena
 * real: seguridad con tokens firmados, el caso de uso, el cliente HTTP con su
 * corta circuitos, el manejador global de la biblioteca de resiliencia y el
 * problem detail que llega al navegador. Lo unico fingido es el modulo ajeno,
 * por HTTP y con la forma de su contrato, y a proposito: la prueba lo apaga y
 * lo enciende, que es lo que CP-01 pide.
 *
 * <p>Los umbrales se acortan (dos fallos, un segundo) para observar la
 * recuperacion sin esperar medio minuto; son los mismos interruptores que el
 * E2E baja por variable de entorno.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "resiliencia.fallos-para-abrir=2",
                "resiliencia.reintentar-en-segundos=1",
                "resiliencia.tiempo-respuesta-ms=1000"
        })
@DisplayName("Degradacion controlada ante la caida del inventario (HU-DIS-003)")
class DegradacionDeInventarioIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    /** Puerto reservado para el doble de inventario, que arranca a mitad de prueba. */
    private static final int PUERTO_INVENTARIO = puertoLibre();

    @DynamicPropertySource
    static void dependencias(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
        registro.add("salas.inventario.url", () -> "http://localhost:" + PUERTO_INVENTARIO);
    }

    /**
     * Sanciones, sano y callado — R10.2.
     *
     * <p>Esta prueba afirma que la caida del inventario degrada SOLO su
     * seccion, asi que todo lo demas tiene que estar en pie. Desde que las
     * puertas de sala comprueban la sancion (HU-USR-005/006), sin este doble
     * la peticion ni siquiera llegaba al inventario: moria antes con un 503
     * `sanciones-no-disponibles`, porque no hay nadie escuchando en el puerto
     * por omision de sanciones. El fallo era real y util —lo destapo CI—,
     * pero medir «la primera dependencia sin doble» no es lo que esta prueba
     * dice medir.
     *
     * <p>El simulacro devuelve {@code false} por omision de Mockito: sin
     * sancion, que es justo lo que hace falta para que la peticion llegue al
     * inventario.
     */
    @MockitoBean
    private SancionesDelJugador sanciones;

    @LocalServerPort
    private int puerto;

    @Autowired
    private RegistroDeDegradacion degradaciones;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private HttpServer inventario;

    @AfterEach
    void apagarElInventario() {
        if (inventario != null) {
            inventario.stop(0);
        }
    }

    @Test
    @DisplayName("inventario colgado: la espera se corta en un segundo y sale como degradacion, no como un 504 del borde")
    void unInventarioQueNoContestaNoCuelgaAlServicio() throws Exception {
        // Lo que destapo el E2E: sin tiempo de espera, un connect a una
        // direccion sin nadie detras se quedaba dos minutos y el borde
        // respondia 504 antes que este servicio. Aqui el doble ACEPTA la
        // conexion y no contesta nunca: con tiempo-respuesta-ms=1000, la
        // respuesta tiene que llegar en un par de segundos y ser la nuestra.
        String token = EmisorDeTokensDePrueba.emisor().tokenDeJugador("Bea", UUID.randomUUID());
        inventario = HttpServer.create(new InetSocketAddress(PUERTO_INVENTARIO), 0);
        inventario.createContext("/", intercambio -> {
            try {
                Thread.sleep(4_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            intercambio.close();
        });
        inventario.start();

        long inicio = System.nanoTime();
        HttpResponse<String> respuesta = crearSala(token);
        long milis = (System.nanoTime() - inicio) / 1_000_000;

        JsonNode problema = json.readTree(respuesta.body());
        assertAll(
                () -> assertEquals(503, respuesta.statusCode(), respuesta.body()),
                () -> assertEquals(ErroresDeDegradacion.TIPO.toString(), problema.path("type").asString()),
                () -> assertTrue(milis < 3_500, "tardo " + milis + " ms: la espera no esta acotada"));
    }

    @Test
    @DisplayName("inventario caido: 503 con la seccion, el listado sigue, y al volver se recupera solo")
    void laCaidaDelInventarioDegradaSoloSuSeccion() throws Exception {
        String token = EmisorDeTokensDePrueba.emisor().tokenDeJugador("Ana", UUID.randomUUID());
        // Por si la prueba del inventario colgado dejo el circuito abierto.
        Thread.sleep(1_100);

        // ---- CP-02: el jugador recibe el aviso explicito de funcion limitada.
        HttpResponse<String> primera = crearSala(token);
        JsonNode problema = json.readTree(primera.body());
        assertAll(
                () -> assertEquals(503, primera.statusCode(), primera.body()),
                () -> assertEquals(ErroresDeDegradacion.TIPO.toString(), problema.path("type").asString()),
                () -> assertEquals(ConfiguracionDeResiliencia.SECCION_INVENTARIO,
                        problema.path("seccion").asString()),
                () -> assertEquals(ConfiguracionDeResiliencia.INVENTARIO,
                        problema.path("dependencia").asString()),
                () -> assertEquals(1, problema.path("reintentarEnSegundos").asInt()),
                () -> assertEquals("1", primera.headers().firstValue("Retry-After").orElse(null)),
                () -> assertTrue(problema.path("title").asString().startsWith("Inventario no disponible"),
                        "el titulo es el ejemplo literal de la HU: " + problema.path("title")),
                () -> assertTrue(problema.path("detail").asString().contains("El resto del juego sigue"),
                        problema.path("detail").asString()));

        // Segundo fallo seguido: el circuito se abre y queda anotado.
        assertEquals(503, crearSala(token).statusCode());
        assertTrue(degradaciones.estaDegradada(ConfiguracionDeResiliencia.INVENTARIO),
                "el registro de degradaciones debe reflejar la caida");

        // ---- CP-01 y CP-03: el servicio no se cayo y lo que no depende del
        // inventario sigue contestando con normalidad.
        HttpResponse<String> listado = http.send(
                peticion("/api/v1/salas").header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> salud = http.send(
                peticion("/actuator/health").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertAll(
                () -> assertEquals(200, listado.statusCode(), listado.body()),
                () -> assertEquals(200, salud.statusCode(), salud.body()),
                () -> assertTrue(salud.body().contains("\"UP\""), salud.body()));

        // ---- El inventario vuelve. Pasada la espera, la llamada de prueba del
        // semiabierto pasa, el circuito se cierra y la sala se crea.
        encenderElInventario();
        Thread.sleep(1_200);

        HttpResponse<String> recuperada = crearSala(token);
        assertAll(
                () -> assertEquals(201, recuperada.statusCode(), recuperada.body()),
                () -> assertFalse(degradaciones.estaDegradada(ConfiguracionDeResiliencia.INVENTARIO),
                        "recuperado el inventario, la degradacion se retira del registro"));
    }

    // ---------------------------------------------------------------------

    private HttpResponse<String> crearSala(String token) throws Exception {
        return http.send(peticion("/api/v1/salas")
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {"maximoParticipantes": 2, "modalidad": "UNO_CONTRA_UNO", "recompensaCreditos": 0}
                                """))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder peticion(String ruta) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta));
    }

    /**
     * Doble de inventario con la forma exacta de {@code inventario.yaml}: una
     * vitrina con un heroe disponible, equipado con un arma, y sus estadisticas.
     */
    private void encenderElInventario() throws IOException {
        inventario = HttpServer.create(new InetSocketAddress(PUERTO_INVENTARIO), 0);
        inventario.createContext("/api/v1/inventario/elementos", intercambio -> responder(intercambio, """
                {"elementos":[{"id":"h-1","tipo":"HEROE","nombrePropio":"Sombra de Vael",
                  "productoId":"p-1","disponible":true,"subastaId":null}],
                 "numero":0,"tamanio":16,"totalElementos":1,"totalPaginas":1,"ultima":true}
                """));
        inventario.createContext("/api/v1/inventario/heroes/h-1/equipamiento", intercambio -> responder(
                intercambio, "{\"heroeId\":\"h-1\",\"armas\":[\"a-1\"],\"armaduras\":{},\"items\":[]}"));
        inventario.createContext("/api/v1/inventario/heroes/h-1/estadisticas", intercambio -> responder(
                intercambio, "{\"heroeId\":\"h-1\",\"vida\":140,\"poder\":10,\"defensa\":4}"));
        inventario.start();
    }

    private static void responder(com.sun.net.httpserver.HttpExchange intercambio, String cuerpo)
            throws IOException {
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
        } catch (IOException e) {
            throw new IllegalStateException("no se pudo reservar un puerto para el doble de inventario", e);
        }
    }
}
