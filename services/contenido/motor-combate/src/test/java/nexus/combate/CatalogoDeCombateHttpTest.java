package nexus.combate;

import com.sun.net.httpserver.HttpServer;
import nexus.combate.reglas.AccionDelCatalogo;
import nexus.combate.reglas.FichaDeCombate;
import nexus.combate.reglas.Formula;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El catalogo de combate por HTTP contra un servicio de heroes falso que habla
 * el JSON de heroes.yaml: el de 1.2.0 y el anterior, sin coste estructurado.
 */
@DisplayName("CatalogoDeCombateHttp · ficha de combate desde el servicio de heroes")
class CatalogoDeCombateHttpTest {

    private static final String FICHA_TANQUE = """
            { "nombre": "Guerrero Tanque", "tipo": "Guerrero", "esSanador": false,
              "descripcion": "Resiste", "estadisticasNivel1": { "poder": 10, "vida": 44, "defensa": 11 },
              "acciones": [
                { "nombre": "Golpe con escudo", "costo": "2 puntos de poder", "efecto": "+2 al ataque",
                  "costoPoder": 2, "todoElPoder": false, "turnosDeCarga": 1, "nivelRequerido": 1 },
                { "nombre": "Mano de piedra", "costo": "4 puntos de poder", "efecto": "+12 a la defensa",
                  "costoPoder": 4, "todoElPoder": false, "turnosDeCarga": 1, "nivelRequerido": 4 },
                { "nombre": "Defensa feroz", "costo": "6 puntos de poder", "efecto": "Inmune",
                  "costoPoder": 6, "todoElPoder": false, "turnosDeCarga": 1, "nivelRequerido": 8 } ] }
            """;

    private static final String VISTA_TANQUE_4 = """
            { "nombre": "Guerrero Tanque", "tipo": "Guerrero", "esSanador": false, "nivel": 4,
              "estadisticas": { "poder": 40, "vida": 176, "defensa": 44,
                                "ataque": "40 + 1d6", "dano": "0 + 1d4", "sanar": null,
                                "ataqueDetalle": { "base": 40, "cantidadDados": 1, "caras": 6 },
                                "danoDetalle": { "base": 0, "cantidadDados": 1, "caras": 4 },
                                "sanarDetalle": null },
              "accionesDisponibles": [], "multiplicadorDeEfecto": 4, "experienciaParaSubir": 3.5,
              "epica": { "nombre": "Golpe de defensa", "efectoGeneral": "+1 al ataque",
                         "efectoPotenciado": "+4 al dano", "turnosDeRecarga": 2 } }
            """;

    /** Un heroes anterior a 1.2.0: el coste solo como texto y sin nivel de desbloqueo. */
    private static final String FICHA_MEDICO_ANTIGUA = """
            { "nombre": "Médico", "tipo": "Sanador", "esSanador": true,
              "acciones": [
                { "nombre": "Curación Directa", "costo": "2 puntos de poder", "efecto": "+2 de sanacion" },
                { "nombre": "Neutralización de Efectos", "costo": "4 puntos de poder", "efecto": "+2" },
                { "nombre": "Reanimación", "costo": "Todos los puntos de poder", "efecto": "100%" } ] }
            """;

    private static final String VISTA_MEDICO_1 = """
            { "nombre": "Médico", "tipo": "Sanador", "esSanador": true, "nivel": 1,
              "estadisticas": { "poder": 10, "vida": 28, "defensa": 4,
                                "ataqueDetalle": null, "danoDetalle": null,
                                "sanarDetalle": { "base": 4, "cantidadDados": 1, "caras": 8 } },
              "epica": { "nombre": "Reanimador 3000" } }
            """;

    private record Respuesta(int estado, String cuerpo) {
    }

    /** Un reloj que solo avanza cuando se le dice. */
    private static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-09-25T10:00:00Z");

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private final Map<String, Respuesta> rutas = new ConcurrentHashMap<>();
    private final List<String> pedidas = new CopyOnWriteArrayList<>();
    private final RelojManual reloj = new RelojManual();
    private HttpServer heroes;

    @BeforeEach
    void levantarHeroesFalso() throws IOException {
        heroes = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        heroes.createContext("/", intercambio -> {
            String ruta = intercambio.getRequestURI().getRawPath();
            pedidas.add(ruta);
            Respuesta respuesta = rutas.getOrDefault(ruta,
                    new Respuesta(404, "{\"detail\":\"El heroe solicitado no esta disponible\"}"));
            byte[] cuerpo = respuesta.cuerpo().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(respuesta.estado(), cuerpo.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(cuerpo);
            }
        });
        heroes.start();
        rutas.put("/api/v1/heroes/Guerrero%20Tanque", new Respuesta(200, FICHA_TANQUE));
        rutas.put("/api/v1/heroes/Guerrero%20Tanque/niveles/4", new Respuesta(200, VISTA_TANQUE_4));
        rutas.put("/api/v1/heroes/Guerrero%20Tanque/niveles/1", new Respuesta(200,
                VISTA_TANQUE_4.replace("\"nivel\": 4", "\"nivel\": 1")));
        rutas.put("/api/v1/heroes/Medico", new Respuesta(200, FICHA_MEDICO_ANTIGUA));
        rutas.put("/api/v1/heroes/Medico/niveles/1", new Respuesta(200, VISTA_MEDICO_1));
    }

    @AfterEach
    void apagarHeroesFalso() {
        heroes.stop(0);
    }

    private CatalogoDeCombateHttp catalogo(Duration vigencia) {
        return new CatalogoDeCombateHttp(URI.create("http://127.0.0.1:" + heroes.getAddress().getPort()),
                HttpClient.newHttpClient(), vigencia, reloj);
    }

    @Test
    @DisplayName("junta la vista por nivel y las tres acciones de la ficha")
    void juntaVistaYFicha() {
        FichaDeCombate ficha = catalogo(Duration.ofMinutes(5)).ficha("Guerrero Tanque", 4);

        assertAll(
                () -> assertEquals("Guerrero Tanque", ficha.prototipo()),
                () -> assertEquals("Guerrero", ficha.tipo()),
                () -> assertFalse(ficha.esSanador()),
                () -> assertEquals(4, ficha.nivel()),
                () -> assertEquals(40, ficha.estadisticas().poder()),
                () -> assertEquals(176, ficha.estadisticas().vida()),
                () -> assertEquals(44, ficha.estadisticas().defensa()),
                () -> assertEquals(new Formula(40, 1, 6), ficha.estadisticas().ataque()),
                () -> assertEquals(new Formula(0, 1, 4), ficha.estadisticas().dano()),
                () -> assertNull(ficha.estadisticas().sanar()),
                () -> assertEquals("Golpe de defensa", ficha.epicaAfin()),
                () -> assertEquals(List.of(
                        new AccionDelCatalogo("Golpe con escudo", 2, false, 1, 1),
                        new AccionDelCatalogo("Mano de piedra", 4, false, 1, 4),
                        new AccionDelCatalogo("Defensa feroz", 6, false, 1, 8)), ficha.acciones()),
                () -> assertEquals(List.of("/api/v1/heroes/Guerrero%20Tanque/niveles/4",
                        "/api/v1/heroes/Guerrero%20Tanque"), pedidas));
    }

    @Test
    @DisplayName("con un heroes anterior a 1.2.0 lee el coste del texto y el nivel de la posicion (RC-01)")
    void formatoAnterior() {
        FichaDeCombate ficha = catalogo(Duration.ZERO).ficha("Médico", 1);

        assertAll(
                () -> assertTrue(ficha.esSanador()),
                () -> assertNull(ficha.estadisticas().ataque()),
                () -> assertEquals(new Formula(4, 1, 8), ficha.estadisticas().sanar()),
                () -> assertEquals(List.of(
                        new AccionDelCatalogo("Curación Directa", 2, false, 1, 1),
                        new AccionDelCatalogo("Neutralización de Efectos", 4, false, 1, 4),
                        new AccionDelCatalogo("Reanimación", null, true, 1, 8)), ficha.acciones()),
                () -> assertTrue(pedidas.contains("/api/v1/heroes/Medico/niveles/1"),
                        "la ruta viaja sin tildes: " + pedidas));
    }

    @Test
    @DisplayName("la ficha se guarda mientras esta vigente y se vuelve a pedir al vencer")
    void cache() {
        CatalogoDeCombateHttp catalogo = catalogo(Duration.ofMinutes(5));

        catalogo.ficha("Guerrero Tanque", 4);
        catalogo.ficha("guerrero tanque", 4);
        assertEquals(2, pedidas.size(), "la segunda vez no se pregunta: tolera mayusculas");

        catalogo.ficha("Guerrero Tanque", 1);
        assertEquals(3, pedidas.size(), "otro nivel: solo su vista; las acciones del prototipo ya estaban");

        reloj.avanzar(Duration.ofMinutes(6));
        catalogo.ficha("Guerrero Tanque", 4);
        assertEquals(5, pedidas.size(), "vencida, se piden otra vez la vista y la ficha");
    }

    @Test
    @DisplayName("con vigencia cero no se guarda nada")
    void sinCache() {
        CatalogoDeCombateHttp catalogo = catalogo(Duration.ZERO);
        catalogo.ficha("Guerrero Tanque", 4);
        catalogo.ficha("Guerrero Tanque", 4);
        assertEquals(4, pedidas.size());
    }

    @Test
    @DisplayName("un fallo no se guarda: la siguiente peticion vuelve a intentarlo")
    void fallosNoSeGuardan() {
        CatalogoDeCombateHttp catalogo = catalogo(Duration.ofMinutes(5));
        Respuesta buena = rutas.put("/api/v1/heroes/Guerrero%20Tanque/niveles/4", new Respuesta(503, "{}"));

        assertThrows(ClienteHeroesException.class, () -> catalogo.ficha("Guerrero Tanque", 4));

        rutas.put("/api/v1/heroes/Guerrero%20Tanque/niveles/4", buena);
        assertEquals(4, catalogo.ficha("Guerrero Tanque", 4).nivel());
    }

    @Test
    @DisplayName("un prototipo que heroes no tiene es HeroeNoEncontrado, no catalogo caido")
    void noExiste() {
        ClienteHeroesException error = assertThrows(ClienteHeroesException.class,
                () -> catalogo(Duration.ZERO).ficha("Arquero del Sur", 1));
        assertInstanceOf(HeroeNoEncontradoException.class, error);
    }

    @Test
    @DisplayName("una respuesta que no se entiende es catalogo caido, no un heroe inventado")
    void respuestasQueNoSirven() {
        rutas.put("/api/v1/heroes/Roto/niveles/1", new Respuesta(200, "esto no es json"));
        rutas.put("/api/v1/heroes/Mudo/niveles/1", new Respuesta(200, "{ \"nombre\": \"Mudo\" }"));
        rutas.put("/api/v1/heroes/Mudo", new Respuesta(200, "{ \"nombre\": \"Mudo\" }"));
        rutas.put("/api/v1/heroes/Gratis/niveles/1", new Respuesta(200, VISTA_MEDICO_1));
        rutas.put("/api/v1/heroes/Gratis", new Respuesta(200,
                "{ \"nombre\": \"Gratis\", \"acciones\": [ { \"nombre\": \"Nada\", \"costo\": \"gratis\" } ] }"));
        CatalogoDeCombateHttp catalogo = catalogo(Duration.ZERO);

        assertAll(
                () -> assertThrows(ClienteHeroesException.class, () -> catalogo.ficha("Roto", 1)),
                () -> assertThrows(ClienteHeroesException.class, () -> catalogo.ficha("Mudo", 1),
                        "sin estadisticas"),
                () -> assertThrows(ClienteHeroesException.class, () -> catalogo.ficha("Gratis", 1),
                        "un coste que no se puede leer"));
    }

    @Test
    @DisplayName("una ficha sin acciones da una ficha sin acciones, no un error")
    void sinAcciones() {
        rutas.put("/api/v1/heroes/Nuevo/niveles/1", new Respuesta(200, VISTA_MEDICO_1));
        rutas.put("/api/v1/heroes/Nuevo", new Respuesta(200, "{ \"nombre\": \"Nuevo\" }"));
        assertTrue(catalogo(Duration.ZERO).ficha("Nuevo", 1).acciones().isEmpty());
    }

    @Test
    @DisplayName("si heroes no escucha, es catalogo caido")
    void heroesApagado() throws IOException {
        int puertoCerrado;
        try (ServerSocket libre = new ServerSocket(0)) {
            puertoCerrado = libre.getLocalPort();
        }
        CatalogoDeCombateHttp apagado = new CatalogoDeCombateHttp(
                URI.create("http://127.0.0.1:" + puertoCerrado), Duration.ofSeconds(1));
        assertThrows(ClienteHeroesException.class, () -> apagado.ficha("Guerrero Tanque", 1));
    }
}
