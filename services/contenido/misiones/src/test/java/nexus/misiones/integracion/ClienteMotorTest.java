package nexus.misiones.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import com.nexusbattles.plataforma.resiliencia.RegistroDeDegradacion;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import nexus.misiones.aplicacion.RechazoDelServicio;
import nexus.misiones.dominio.simulacion.AccionNoPermitida;
import nexus.misiones.dominio.simulacion.Combatiente;
import nexus.misiones.dominio.simulacion.DetalleDeAtaque;
import nexus.misiones.dominio.simulacion.EfectoActivo;
import nexus.misiones.dominio.simulacion.EstadisticasDeCombate;
import nexus.misiones.dominio.simulacion.Formula;
import nexus.misiones.dominio.simulacion.MotorDeCombate;
import nexus.misiones.dominio.simulacion.ResultadoDeAccion;
import nexus.misiones.dominio.simulacion.ResultadoDeTurno;
import nexus.misiones.dominio.simulacion.Suceso;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * El cliente del motor de combate (motor-combate.yaml 1.2.0) contra un servidor
 * HTTP de verdad: lo que manda en {@code POST /api/v1/combate/turnos} y
 * {@code POST /api/v1/combate/acciones}, como lee la respuesta y que hace con
 * un 409 {@code accion-no-permitida}. Es el MISMO contrato que usa
 * salas-partidas para las batallas en linea.
 */
class ClienteMotorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final EstadisticasDeCombate ESTADISTICAS =
            new EstadisticasDeCombate(12, 60, 14, new Formula(11, 1, 6), new Formula(3, 1, 4), null);

    private static final Combatiente HEROE = new Combatiente("heroe", "Guerrero Armas", 5, ESTADISTICAS, 55, null, 0,
            Map.of(), List.of(), List.of("Espada de una mano"), List.of("Golpe de defensa"), null, Map.of(), List.of());
    private static final Combatiente RIVAL = Combatiente.alEmpezar("rival", "Guerrero Tanque", 5, null, 40,
            List.of(), List.of());

    private HttpServer servidor;
    private final List<String[]> recibidas = Collections.synchronizedList(new ArrayList<>());
    private volatile int estado = 200;
    private volatile String contenido = "{}";
    private ClienteMotor cliente;

    @BeforeEach
    void arrancar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            recibidas.add(new String[] {intercambio.getRequestMethod(), intercambio.getRequestURI().getPath(), cuerpo});
            byte[] bytes = contenido.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().set("Content-Type", estado == 200 ? "application/json"
                    : "application/problem+json");
            intercambio.sendResponseHeaders(estado, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                intercambio.getResponseBody().write(bytes);
            }
            intercambio.close();
        });
        servidor.start();
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        RestClient rest = RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(http)).build();
        // Con barra final, como la escribiria alguien en el .env.
        cliente = new ClienteMotor(rest, "http://127.0.0.1:" + servidor.getAddress().getPort() + "/",
                new CortaCircuitos("motor-combate", "Motor de combate", 100, Duration.ofSeconds(30),
                        Clock.systemUTC(), new RegistroDeDegradacion()));
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cuerpoDe(int indice) {
        return JSON.readValue(recibidas.get(indice)[2], Map.class);
    }

    // ------------------------------------------------------------------ turnos

    private static final String RESPUESTA_DE_TURNO = """
            {
              "combatiente": "heroe",
              "afectados": [{"id": "heroe", "vidaAntes": 55, "vidaDespues": 55, "diferencia": 0}],
              "eventos": [{"tipo": "PODER_RECUPERADO", "combatiente": "heroe", "origen": null, "efecto": null,
                           "cantidad": 2}],
              "combatientes": [
                {"id": "heroe", "equipo": null, "prototipo": "Guerrero Armas", "nivel": 5,
                 "estadisticas": {"poder": 12, "vida": 60, "defensa": 14,
                                  "ataque": {"base": 11, "cantidadDados": 1, "caras": 6},
                                  "dano": {"base": 3, "cantidadDados": 1, "caras": 4}, "sanar": null},
                 "vidaActual": 55, "poderActual": 12, "turnosJugados": 3,
                 "cargas": {"Embate sangriento": 2},
                 "efectos": [{"codigo": "SANGRADO", "nombre": "Sangrado", "tipo": "DANO_POR_TURNO", "valor": 2,
                              "turnos": 1, "hastaSuTurno": false, "origen": "rival"}],
                 "equipamiento": ["Espada de una mano"], "epicas": ["Golpe de defensa"],
                 "ultimoDanoRecibido": {"de": "rival", "cantidad": 5},
                 "recargas": {"Embate sangriento": 1},
                 "acciones": [{"codigo": "ATAQUE_BASICO", "nombre": "Ataque básico", "tipo": "ATAQUE",
                               "disponible": true},
                              {"codigo": "Embate sangriento", "nombre": "Embate sangriento", "tipo": "ATAQUE",
                               "disponible": false, "motivo": "En carga"}]},
                {"id": "rival", "prototipo": "Guerrero Tanque", "nivel": 5,
                 "estadisticas": {"poder": 10, "vida": 40, "defensa": 11,
                                  "ataque": {"base": 9, "cantidadDados": 1, "caras": 6},
                                  "dano": {"base": 1, "cantidadDados": 1, "caras": 4}},
                 "vidaActual": 40, "poderActual": 10, "turnosJugados": 0}
              ]
            }
            """;

    @Test
    @DisplayName("iniciar un turno es POST /api/v1/combate/turnos con el estado de todos, sin barra doble")
    void iniciarTurnoPide() {
        contenido = RESPUESTA_DE_TURNO;

        cliente.iniciarTurno("heroe", List.of(HEROE, RIVAL), null);

        assertThat(recibidas).hasSize(1);
        assertThat(recibidas.getFirst()[0]).isEqualTo("POST");
        assertThat(recibidas.getFirst()[1]).isEqualTo("/api/v1/combate/turnos");
        Map<String, Object> cuerpo = cuerpoDe(0);
        assertThat(cuerpo).containsEntry("combatiente", "heroe").containsEntry("porEquipos", false);
        assertThat(cuerpo.get("semilla")).isNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> combatientes = (List<Map<String, Object>>) cuerpo.get("combatientes");
        assertThat(combatientes).hasSize(2);

        Map<String, Object> heroe = combatientes.get(0);
        assertThat(heroe).containsEntry("id", "heroe").containsEntry("prototipo", "Guerrero Armas")
                .containsEntry("nivel", 5).containsEntry("vidaActual", 55).containsEntry("turnosJugados", 0);
        assertThat(heroe.get("poderActual")).isNull();
        assertThat(heroe.get("equipamiento")).isEqualTo(List.of("Espada de una mano"));
        assertThat(heroe.get("epicas")).isEqualTo(List.of("Golpe de defensa"));
        @SuppressWarnings("unchecked")
        Map<String, Object> estadisticas = (Map<String, Object>) heroe.get("estadisticas");
        assertThat(estadisticas).containsEntry("poder", 12).containsEntry("vida", 60).containsEntry("defensa", 14)
                .containsEntry("ataque", Map.of("base", 11, "cantidadDados", 1, "caras", 6))
                .containsEntry("dano", Map.of("base", 3, "cantidadDados", 1, "caras", 4));
        assertThat(estadisticas.get("sanar")).isNull();

        // Un rival sin estadisticas conocidas se manda sin ellas: el motor usa las del catalogo.
        assertThat(combatientes.get(1).get("estadisticas")).isNull();
    }

    @Test
    @DisplayName("la semilla de pruebas viaja cuando la hay")
    void semillaViaja() {
        contenido = RESPUESTA_DE_TURNO;

        cliente.iniciarTurno("heroe", List.of(HEROE, RIVAL), 42L);

        assertThat(cuerpoDe(0)).containsEntry("semilla", 42);
    }

    @Test
    @DisplayName("la respuesta de un turno se lee entera: sucesos, estadisticas, cargas, efectos y acciones")
    void iniciarTurnoLee() {
        contenido = RESPUESTA_DE_TURNO;

        ResultadoDeTurno resultado = cliente.iniciarTurno("heroe", List.of(HEROE, RIVAL), null);

        assertThat(resultado.combatiente()).isEqualTo("heroe");
        assertThat(resultado.sucesos())
                .containsExactly(new Suceso("PODER_RECUPERADO", "heroe", null, null, 2));
        Combatiente heroe = resultado.combatientes().getFirst();
        assertThat(heroe.id()).isEqualTo("heroe");
        assertThat(heroe.prototipo()).isEqualTo("Guerrero Armas");
        assertThat(heroe.nivel()).isEqualTo(5);
        assertThat(heroe.estadisticas()).isEqualTo(ESTADISTICAS);
        assertThat(heroe.vidaActual()).isEqualTo(55);
        assertThat(heroe.poderActual()).isEqualTo(12);
        assertThat(heroe.turnosJugados()).isEqualTo(3);
        assertThat(heroe.cargas()).containsEntry("Embate sangriento", 2);
        assertThat(heroe.efectos()).containsExactly(
                new EfectoActivo("SANGRADO", "Sangrado", "DANO_POR_TURNO", 2, 1, false, "rival"));
        assertThat(heroe.equipamiento()).containsExactly("Espada de una mano");
        assertThat(heroe.epicas()).containsExactly("Golpe de defensa");
        assertThat(heroe.ultimoDanoRecibido()).isEqualTo(new Combatiente.GolpeRecibido("rival", 5));
        assertThat(heroe.recargas()).containsEntry("Embate sangriento", 1);
        assertThat(heroe.acciones()).containsExactly("ATAQUE_BASICO", "Embate sangriento");
    }

    @Test
    @DisplayName("si la respuesta no repite el prototipo, el nivel o el equipo, se conservan los que se mandaron")
    void conservaLoQueSeMando() {
        contenido = RESPUESTA_DE_TURNO.replace("\"prototipo\": \"Guerrero Tanque\", \"nivel\": 5,", "");

        ResultadoDeTurno resultado = cliente.iniciarTurno("heroe", List.of(HEROE, RIVAL), null);

        Combatiente rival = resultado.combatientes().get(1);
        assertThat(rival.prototipo()).isEqualTo("Guerrero Tanque");
        assertThat(rival.nivel()).isEqualTo(5);
        assertThat(rival.estadisticas()).isEqualTo(new EstadisticasDeCombate(10, 40, 11, new Formula(9, 1, 6),
                new Formula(1, 1, 4), null));
        assertThat(rival.vidaActual()).isEqualTo(40);
    }

    // ----------------------------------------------------------------- acciones

    private static final String RESPUESTA_DE_ACCION = """
            {
              "accion": "Embate sangriento", "accionEjecutada": "ATAQUE_BASICO", "enValorBase": true,
              "ejecutor": "heroe", "objetivo": "rival", "tipo": "ATAQUE", "esEpica": false, "potenciada": false,
              "ataque": {"ataqueResuelto": 17, "defensaObjetivo": 11, "acierta": true,
                         "categoria": "CAUSAR_DANO_CRITICO", "indiceTabla": 4012, "porcentajeDano": 150,
                         "danoBase": 8, "danoAplicado": 12},
              "afectados": [{"id": "rival", "vidaAntes": 40, "vidaDespues": 28, "diferencia": -12}],
              "eventos": [{"tipo": "VALOR_BASE", "combatiente": "heroe", "origen": null,
                           "efecto": "Embate sangriento", "cantidad": null},
                          {"tipo": "DANO", "combatiente": "rival", "origen": "heroe", "efecto": "Ataque básico",
                           "cantidad": 12}],
              "combatientes": [
                {"id": "heroe", "prototipo": "Guerrero Armas", "nivel": 5, "vidaActual": 55, "poderActual": 12,
                 "turnosJugados": 1, "acciones": []},
                {"id": "rival", "prototipo": "Guerrero Tanque", "nivel": 5, "vidaActual": 28, "poderActual": 10,
                 "turnosJugados": 0}
              ]
            }
            """;

    @Test
    @DisplayName("resolver una accion es POST /api/v1/combate/acciones con la accion, el ejecutor y el objetivo")
    void resolverAccionPide() {
        contenido = RESPUESTA_DE_ACCION;

        cliente.resolverAccion("Embate sangriento", "heroe", "rival", List.of(HEROE, RIVAL), 7L);

        assertThat(recibidas.getFirst()[1]).isEqualTo("/api/v1/combate/acciones");
        Map<String, Object> cuerpo = cuerpoDe(0);
        assertThat(cuerpo).containsEntry("accion", "Embate sangriento").containsEntry("ejecutor", "heroe")
                .containsEntry("objetivo", "rival").containsEntry("porEquipos", false).containsEntry("semilla", 7);
        assertThat((List<?>) cuerpo.get("combatientes")).hasSize(2);
    }

    @Test
    @DisplayName("la respuesta de una accion se lee entera: el golpe, el valor base y los sucesos")
    void resolverAccionLee() {
        contenido = RESPUESTA_DE_ACCION;

        ResultadoDeAccion resultado = cliente.resolverAccion("Embate sangriento", "heroe", "rival",
                List.of(HEROE, RIVAL), null);

        assertThat(resultado.accion()).isEqualTo("Embate sangriento");
        assertThat(resultado.accionEjecutada()).isEqualTo(MotorDeCombate.ATAQUE_BASICO);
        assertThat(resultado.enValorBase()).isTrue();
        assertThat(resultado.ejecutor()).isEqualTo("heroe");
        assertThat(resultado.objetivo()).isEqualTo("rival");
        assertThat(resultado.ataque()).isEqualTo(
                new DetalleDeAtaque(17, 11, true, "CAUSAR_DANO_CRITICO", 4012, 150, 8, 12));
        assertThat(resultado.ataque().critico()).isTrue();
        assertThat(resultado.sucesos()).containsExactly(
                new Suceso("VALOR_BASE", "heroe", null, "Embate sangriento", null),
                new Suceso("DANO", "rival", "heroe", "Ataque básico", 12));
        assertThat(resultado.combatientes()).extracting(Combatiente::vidaActual).containsExactly(55, 28);
        // Lo que el motor no repite se conserva de lo que se le mando.
        assertThat(resultado.combatientes().getFirst().equipamiento()).containsExactly("Espada de una mano");
        assertThat(resultado.combatientes().getFirst().estadisticas()).isEqualTo(ESTADISTICAS);
    }

    @Test
    @DisplayName("una accion que no golpea llega sin detalle de ataque")
    void sinAtaque() {
        contenido = """
                {"accion": "Defensa feroz", "accionEjecutada": "Defensa feroz", "enValorBase": false,
                 "ejecutor": "heroe", "objetivo": "heroe", "tipo": "DEFENSA", "ataque": null,
                 "afectados": [], "eventos": [],
                 "combatientes": [{"id": "heroe", "vidaActual": 55}, {"id": "rival", "vidaActual": 40}]}
                """;

        ResultadoDeAccion resultado = cliente.resolverAccion("Defensa feroz", "heroe", null, List.of(HEROE, RIVAL),
                null);

        assertThat(resultado.ataque()).isNull();
        assertThat(resultado.enValorBase()).isFalse();
        assertThat(cuerpoDe(0).get("objetivo")).isNull();
    }

    // ------------------------------------------------------------------ errores

    @Test
    @DisplayName("un 409 accion-no-permitida es AccionNoPermitida con el motivo y el detalle del motor")
    void accionNoPermitida() {
        estado = 409;
        contenido = """
                {"type": "https://nexusbattles.local/errores/accion-no-permitida",
                 "title": "La accion no se puede jugar ahora", "status": 409,
                 "detail": "Golpe de tormenta esta en carga: vuelve a estar disponible dentro de 1 turno.",
                 "motivo": "EN_CARGA"}
                """;

        assertThatThrownBy(() -> cliente.resolverAccion("Golpe de tormenta", "heroe", "rival",
                List.of(HEROE, RIVAL), null))
                .isInstanceOfSatisfying(AccionNoPermitida.class, e -> {
                    assertThat(e.motivo()).isEqualTo("EN_CARGA");
                    assertThat(e.getMessage()).contains("en carga");
                });
    }

    @Test
    @DisplayName("un 409 sin cuerpo legible tambien es AccionNoPermitida: el motor dijo que no")
    void accionNoPermitidaSinCuerpo() {
        estado = 409;
        contenido = "";

        assertThatThrownBy(() -> cliente.resolverAccion("Golpe de tormenta", "heroe", "rival",
                List.of(HEROE, RIVAL), null))
                .isInstanceOfSatisfying(AccionNoPermitida.class, e -> assertThat(e.motivo()).isEqualTo("DESCONOCIDO"));
    }

    @Test
    @DisplayName("un 5xx es una dependencia degradada: la simulacion se reintenta despues")
    void motorCaido() {
        estado = 503;
        contenido = "{\"title\": \"caido\", \"status\": 503}";

        assertThatThrownBy(() -> cliente.iniciarTurno("heroe", List.of(HEROE, RIVAL), null))
                .isInstanceOf(DependenciaDegradada.class);
    }

    @Test
    @DisplayName("un 400 o un 404 son un rechazo del servicio, no una caida: no abren el circuito")
    void rechazoDelServicio() {
        estado = 404;
        contenido = "{\"title\": \"El heroe no existe\", \"status\": 404, \"detail\": \"No hay ningun heroe llamado X.\"}";

        assertThatThrownBy(() -> cliente.iniciarTurno("heroe", List.of(HEROE, RIVAL), null))
                .isInstanceOfSatisfying(RechazoDelServicio.class, e -> {
                    assertThat(e.servicio()).isEqualTo("motor-combate");
                    assertThat(e.estado()).isEqualTo(404);
                });
    }

    @Test
    @DisplayName("una respuesta sin el estado de los combatientes no sirve: no se inventa un combate")
    void respuestaIlegible() {
        contenido = "{\"combatiente\": \"heroe\"}";

        assertThatThrownBy(() -> cliente.iniciarTurno("heroe", List.of(HEROE, RIVAL), null))
                .isInstanceOf(RechazoDelServicio.class);
    }

    @Test
    @DisplayName("jamas llama al ataque suelto: solo turnos y acciones")
    void soloTurnosYAcciones() {
        contenido = RESPUESTA_DE_TURNO;
        cliente.iniciarTurno("heroe", List.of(HEROE, RIVAL), null);
        contenido = RESPUESTA_DE_ACCION;
        cliente.resolverAccion("ATAQUE_BASICO", "heroe", "rival", List.of(HEROE, RIVAL), null);

        assertThat(recibidas).extracting(r -> r[1])
                .containsExactly("/api/v1/combate/turnos", "/api/v1/combate/acciones");
    }
}
