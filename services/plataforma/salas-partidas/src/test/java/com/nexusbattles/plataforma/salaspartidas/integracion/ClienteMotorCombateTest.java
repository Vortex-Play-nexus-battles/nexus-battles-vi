package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import com.nexusbattles.plataforma.resiliencia.EstadoDelCorta;
import com.nexusbattles.plataforma.resiliencia.RegistroDeDegradacion;
import com.nexusbattles.plataforma.salaspartidas.configuracion.ConfiguracionDeResiliencia;
import com.nexusbattles.plataforma.salaspartidas.dominio.AccionNoPermitida;
import com.nexusbattles.plataforma.salaspartidas.dominio.CombatienteResuelto;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadisticasDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.InicioDeTurno;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PerfilDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResolucionDeAccion;
import com.nexusbattles.plataforma.salaspartidas.dominio.Turno;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * El cliente que de verdad habla con el motor de combate (motor-combate.yaml 1.2.0).
 *
 * <p>El transporte HTTP esta simulado, pero lo que importa NO lo esta: la
 * serializacion de Jackson, la URI, el cuerpo que se compone desde la partida,
 * el mapeo de la respuesta y la traduccion de errores son los reales. Todo el
 * combate de {@code EjecutarAccionTest} usa un doble del puerto; el codigo que
 * corre en produccion cuando un jugador juega es este.
 */
@DisplayName("ClienteMotorCombate · el adaptador HTTP que corre en produccion")
class ClienteMotorCombateTest {

    private static final String BASE = "http://srv-motor-combate:8080";
    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CARLA = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private MockRestServiceServer motor;
    private ClienteMotorCombate cliente;
    private CortaCircuitos corta;

    @BeforeEach
    void prepararElMotor() {
        RestClient.Builder constructor = RestClient.builder();
        motor = MockRestServiceServer.bindTo(constructor).build();
        corta = new CortaCircuitos(ConfiguracionDeResiliencia.MOTOR_COMBATE,
                ConfiguracionDeResiliencia.SECCION_COMBATE, 2, Duration.ofSeconds(30),
                Clock.systemUTC(), new RegistroDeDegradacion());
        cliente = new ClienteMotorCombate(constructor.build(), BASE + "/", corta);
    }

    /**
     * Ana (Guerrero Tanque de nivel 4, con perfil y estado), Bruno (Mago Hielo
     * sin estado todavia) y Carla (ficha anterior a V8: sin prototipo).
     */
    private static Partida partida() {
        EstadisticasDeCombate enNivel4 = new EstadisticasDeCombate(40, 178, 45,
                new EstadisticasDeCombate.Formula(41, 1, 6), new EstadisticasDeCombate.Formula(0, 1, 4), null);
        HeroeDeCombate tanque = new HeroeDeCombate("h-1", "Muro", "Guerrero Tanque", null, 4, 150, 178, 45,
                new PerfilDeCombate(4, enNivel4, List.of("Espada de una mano"), List.of("Golpe de defensa")));
        EstadoDeCombate estadoDeAna = new EstadoDeCombate(8, 40, 3, Map.of("Golpe con escudo", 2),
                List.of(new EstadoDeCombate.Efecto("CORTADA", "Cortada", "BONO_DANO", 2, 1, false, ANA.toString())),
                new EstadoDeCombate.GolpeRecibido(BRUNO.toString(), 7), Map.of(), List.of(), enNivel4);
        HeroeDeCombate mago = new HeroeDeCombate("h-2", "Escarcha", "Mago Hielo", null, 1, 40, 40, 10);
        HeroeDeCombate antiguo = new HeroeDeCombate("h-3", "Viejo", null, 1, 30, 30);
        return Partida.rehidratar(UUID.randomUUID(), UUID.randomUUID(), EstadoPartida.EN_CURSO, List.of(
                        new ParticipanteDePartida(ANA, tanque, false, null, 0, estadoDeAna),
                        new ParticipanteDePartida(BRUNO, mago, false, null, 0),
                        new ParticipanteDePartida(CARLA, antiguo, false, null, 0)),
                Turno.primero(ANA), 0, Instant.parse("2026-09-25T10:00:00Z"));
    }

    private static final String COMBATIENTES_DE_VUELTA = """
            [
              { "id": "%s", "prototipo": "Guerrero Tanque", "nivel": 4,
                "estadisticas": { "poder": 40, "vida": 178, "defensa": 45,
                                  "ataque": { "base": 41, "cantidadDados": 1, "caras": 6 },
                                  "dano": { "base": 0, "cantidadDados": 1, "caras": 4 }, "sanar": null },
                "vidaActual": 150, "poderActual": 6, "turnosJugados": 4,
                "cargas": { "Golpe con escudo": 2, "Mano de piedra": 3 },
                "efectos": [ { "codigo": "MANO_DE_PIEDRA", "nombre": "Mano de piedra", "tipo": "BONO_DEFENSA",
                               "valor": 48, "turnos": 1, "hastaSuTurno": true, "origen": "%s" } ],
                "ultimoDanoRecibido": { "de": "%s", "cantidad": 7 },
                "recargas": { "Mano de piedra": 1 },
                "acciones": [ { "codigo": "Mano de piedra", "nombre": "Mano de piedra", "tipo": "DEFENSA",
                                "esEpica": false, "costoPoder": 4, "todoElPoder": false, "turnosDeCarga": 1,
                                "nivelRequerido": 4, "disponible": false, "motivo": "En carga: 1 turno." } ],
                "campoNuevo": "no rompe" },
              { "id": "%s", "prototipo": "Mago Hielo", "nivel": 1,
                "estadisticas": { "poder": 10, "vida": 40, "defensa": 10,
                                  "ataque": { "base": 10, "cantidadDados": 1, "caras": 8 },
                                  "dano": { "base": 0, "cantidadDados": 1, "caras": 6 } },
                "vidaActual": 33, "poderActual": 10, "turnosJugados": 0, "cargas": {}, "efectos": [],
                "recargas": {}, "acciones": [] }
            ]
            """.formatted(ANA, ANA, BRUNO, BRUNO);

    // =====================================================================
    // Lo que se manda
    // =====================================================================

    @Test
    @DisplayName("pide la accion a la ruta del contrato, con el estado de TODOS los combatientes")
    void mandaLoQueElContratoDice() {
        motor.expect(requestTo(BASE + "/api/v1/combate/acciones"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.accion").value("Mano de piedra"))
                .andExpect(jsonPath("$.ejecutor").value(ANA.toString()))
                .andExpect(jsonPath("$.objetivo").value(nullValue()))
                .andExpect(jsonPath("$.porEquipos").value(false))
                .andExpect(jsonPath("$.combatientes.length()").value(2))
                .andExpect(jsonPath("$.combatientes[0].id").value(ANA.toString()))
                .andExpect(jsonPath("$.combatientes[0].prototipo").value("Guerrero Tanque"))
                .andExpect(jsonPath("$.combatientes[0].nivel").value(4))
                .andExpect(jsonPath("$.combatientes[0].estadisticas.vida").value(178))
                .andExpect(jsonPath("$.combatientes[0].estadisticas.ataque.base").value(41))
                .andExpect(jsonPath("$.combatientes[0].vidaActual").value(150))
                .andExpect(jsonPath("$.combatientes[0].poderActual").value(8))
                .andExpect(jsonPath("$.combatientes[0].turnosJugados").value(3))
                .andExpect(jsonPath("$.combatientes[0].cargas['Golpe con escudo']").value(2))
                .andExpect(jsonPath("$.combatientes[0].efectos[0].tipo").value("BONO_DANO"))
                .andExpect(jsonPath("$.combatientes[0].equipamiento[0]").value("Espada de una mano"))
                .andExpect(jsonPath("$.combatientes[0].epicas[0]").value("Golpe de defensa"))
                .andExpect(jsonPath("$.combatientes[0].ultimoDanoRecibido.de").value(BRUNO.toString()))
                .andExpect(jsonPath("$.combatientes[1].id").value(BRUNO.toString()))
                .andExpect(jsonPath("$.combatientes[1].nivel").value(1))
                .andExpect(jsonPath("$.combatientes[1].estadisticas").value(nullValue()))
                .andExpect(jsonPath("$.combatientes[1].poderActual").value(nullValue()))
                .andRespond(withSuccess("""
                        { "accion": "Mano de piedra", "accionEjecutada": "Mano de piedra", "enValorBase": false,
                          "ejecutor": "%s", "objetivo": "%s", "tipo": "DEFENSA", "esEpica": false,
                          "potenciada": false, "ataque": null, "afectados": [], "eventos": [],
                          "combatientes": %s }
                        """.formatted(ANA, ANA, COMBATIENTES_DE_VUELTA), MediaType.APPLICATION_JSON));

        cliente.resolverAccion("Mano de piedra", ANA, null, partida());

        motor.verify();
    }

    @Test
    @DisplayName("traduce la accion resuelta: tirada, afectados, eventos y el estado de cada uno")
    void traduceLaRespuesta() {
        motor.expect(requestTo(BASE + "/api/v1/combate/acciones"))
                .andRespond(withSuccess("""
                        { "accion": "DECISION_DE_LA_MAQUINA", "accionEjecutada": "ATAQUE_BASICO", "enValorBase": true,
                          "ejecutor": "%s", "objetivo": "%s", "tipo": "ATAQUE", "esEpica": false, "potenciada": false,
                          "ataque": { "ataqueResuelto": 18, "defensaObjetivo": 10, "acierta": true,
                                      "categoria": "CAUSAR_DANO_CRITICO", "indiceTabla": 4900,
                                      "porcentajeDano": 150, "danoBase": 5, "danoAplicado": 7 },
                          "afectados": [ { "id": "%s", "vidaAntes": 40, "vidaDespues": 33, "diferencia": -7 } ],
                          "eventos": [ { "tipo": "DANO", "combatiente": "%s", "origen": "%s",
                                         "efecto": "Ataque básico", "cantidad": 7 } ],
                          "combatientes": %s }
                        """.formatted(ANA, BRUNO, BRUNO, BRUNO, ANA, COMBATIENTES_DE_VUELTA),
                        MediaType.APPLICATION_JSON));

        ResolucionDeAccion r = cliente.resolverAccion(MotorDeCombate.DECISION_DE_LA_MAQUINA, ANA, null, partida());

        CombatienteResuelto ana = r.combatientes().get(0);
        assertAll(
                () -> assertEquals("ATAQUE_BASICO", r.accionEjecutada()),
                () -> assertTrue(r.enValorBase()),
                () -> assertEquals(BRUNO, r.objetivo()),
                () -> assertEquals("CAUSAR_DANO_CRITICO", r.ataque().categoria()),
                () -> assertEquals(150, r.ataque().porcentajeDano()),
                () -> assertEquals(-7, r.afectados().get(0).diferencia()),
                () -> assertEquals(ANA, r.eventos().get(0).origen()),
                () -> assertEquals(178, ana.vidaMaxima(), "la maxima sale de las estadisticas del motor"),
                () -> assertEquals(6, ana.estado().poderActual()),
                () -> assertEquals(40, ana.estado().poderMaximo()),
                () -> assertEquals(4, ana.estado().turnosJugados()),
                () -> assertEquals(Map.of("Mano de piedra", 1), ana.estado().recargas()),
                () -> assertTrue(ana.estado().efectos().get(0).hastaSuTurno()),
                () -> assertEquals("En carga: 1 turno.", ana.estado().acciones().get(0).motivo()),
                () -> assertEquals(BRUNO.toString(), ana.estado().ultimoDanoRecibido().de()),
                () -> assertEquals(45, ana.estado().estadisticas().defensa()),
                () -> assertEquals(33, r.combatientes().get(1).vidaActual()));
    }

    @Test
    @DisplayName("el comienzo de turno va a /turnos; al empezar la partida, todos a vida completa")
    void comienzoDeTurno() {
        motor.expect(requestTo(BASE + "/api/v1/combate/turnos"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.combatiente").value(BRUNO.toString()))
                .andExpect(jsonPath("$.combatientes[0].vidaActual").value(Integer.MAX_VALUE))
                .andExpect(jsonPath("$.combatientes[0].poderActual").value(nullValue()))
                .andRespond(withSuccess("""
                        { "combatiente": "%s",
                          "afectados": [ { "id": "%s", "vidaAntes": 40, "vidaDespues": 36, "diferencia": -4 } ],
                          "eventos": [ { "tipo": "DANO_POR_TURNO", "combatiente": "%s", "origen": "%s",
                                         "efecto": "Cierra sangrienta", "cantidad": 4 } ],
                          "combatientes": %s }
                        """.formatted(BRUNO, BRUNO, BRUNO, ANA, COMBATIENTES_DE_VUELTA), MediaType.APPLICATION_JSON));

        InicioDeTurno inicio = cliente.iniciarTurno(BRUNO, partida(), true);

        assertAll(
                () -> assertEquals(BRUNO, inicio.combatiente()),
                () -> assertTrue(inicio.eventos().get(0).esEfectoPorTurno()),
                () -> assertEquals(2, inicio.combatientes().size()));
        motor.verify();
    }

    // =====================================================================
    // Cuando el motor dice que no, o falla
    // =====================================================================

    @Test
    @DisplayName("un 409 del motor es AccionNoPermitida, con su motivo y su detalle")
    void elRechazoLlegaConSuMotivo() {
        motor.expect(requestTo(BASE + "/api/v1/combate/acciones"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                { "type": "https://nexusbattles.local/errores/accion-no-permitida",
                                  "title": "La accion no se puede jugar ahora", "status": 409,
                                  "detail": "Golpe con escudo está en carga: vuelve a estar disponible dentro de 1 turno.",
                                  "motivo": "EN_CARGA" }
                                """));

        AccionNoPermitida rechazo = assertThrows(AccionNoPermitida.class,
                () -> cliente.resolverAccion("Golpe con escudo", ANA, BRUNO, partida()));

        assertAll(
                () -> assertEquals("EN_CARGA", rechazo.motivo()),
                () -> assertTrue(rechazo.detalle().contains("en carga")),
                () -> assertEquals(409, rechazo.estado()),
                () -> assertEquals(EstadoDelCorta.CERRADO, corta.estado(), "el motor contesto: no es una caida"));
    }

    @Test
    @DisplayName("un 409 sin cuerpo legible sigue siendo un rechazo, con textos por omision")
    void rechazoSinCuerpo() {
        motor.expect(requestTo(BASE + "/api/v1/combate/acciones"))
                .andRespond(withStatus(HttpStatus.CONFLICT).body("no es json"));

        AccionNoPermitida rechazo = assertThrows(AccionNoPermitida.class,
                () -> cliente.resolverAccion("ATAQUE_BASICO", ANA, BRUNO, partida()));

        assertEquals("ACCION_DESCONOCIDA", rechazo.motivo());
    }

    @Test
    @DisplayName("un 400 del motor es motor no disponible: contesto, asi que el circuito no se abre")
    void unCuatrocientosEsMotorNoDisponible() {
        motor.expect(requestTo(BASE + "/api/v1/combate/acciones"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"title\":\"La peticion no cumple las reglas del combate\"}"));

        MotorNoDisponible error = assertThrows(MotorNoDisponible.class,
                () -> cliente.resolverAccion("ATAQUE_BASICO", ANA, BRUNO, partida()));

        assertAll(
                () -> assertEquals(503, error.estado()),
                () -> assertTrue(error.detalle().contains("400"), error.detalle()),
                () -> assertEquals(EstadoDelCorta.CERRADO, corta.estado()));
    }

    @Test
    @DisplayName("un 5xx es la seccion de combate degradada; dos seguidos abren el circuito (HU-DIS-003)")
    void elErrorDelServidorEsDegradacion() {
        motor.expect(requestTo(BASE + "/api/v1/combate/acciones")).andRespond(withServerError());
        motor.expect(requestTo(BASE + "/api/v1/combate/turnos")).andRespond(withServerError());

        DependenciaDegradada error = assertThrows(DependenciaDegradada.class,
                () -> cliente.resolverAccion("ATAQUE_BASICO", ANA, BRUNO, partida()));
        assertThrows(DependenciaDegradada.class, () -> cliente.iniciarTurno(BRUNO, partida(), false));
        DependenciaDegradada sinLlamar = assertThrows(DependenciaDegradada.class,
                () -> cliente.resolverAccion("ATAQUE_BASICO", ANA, BRUNO, partida()));

        motor.verify();
        assertAll(
                () -> assertEquals(ConfiguracionDeResiliencia.MOTOR_COMBATE, error.dependencia()),
                () -> assertEquals(ConfiguracionDeResiliencia.SECCION_COMBATE, error.seccion()),
                () -> assertEquals(EstadoDelCorta.ABIERTO, corta.estado()),
                () -> assertNull(sinLlamar.getCause()));
    }

    @Test
    @DisplayName("un cuerpo vacio, o sin lo esencial, no se toma por una resolucion")
    void respuestasQueNoSirven() {
        motor.expect(requestTo(BASE + "/api/v1/combate/acciones"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        motor.expect(requestTo(BASE + "/api/v1/combate/acciones"))
                .andRespond(withSuccess("{\"accion\":\"ATAQUE_BASICO\"}", MediaType.APPLICATION_JSON));
        motor.expect(requestTo(BASE + "/api/v1/combate/turnos"))
                .andRespond(withSuccess("{\"afectados\":[]}", MediaType.APPLICATION_JSON));
        motor.expect(requestTo(BASE + "/api/v1/combate/acciones"))
                .andRespond(withSuccess("""
                        { "accionEjecutada": "ATAQUE_BASICO", "ejecutor": "no-es-un-uuid", "combatientes": [] }
                        """, MediaType.APPLICATION_JSON));

        assertAll(
                () -> assertThrows(MotorNoDisponible.class,
                        () -> cliente.resolverAccion("ATAQUE_BASICO", ANA, BRUNO, partida())),
                () -> assertThrows(MotorNoDisponible.class,
                        () -> cliente.resolverAccion("ATAQUE_BASICO", ANA, BRUNO, partida())),
                () -> assertThrows(MotorNoDisponible.class, () -> cliente.iniciarTurno(BRUNO, partida(), false)),
                () -> assertThrows(MotorNoDisponible.class,
                        () -> cliente.resolverAccion("ATAQUE_BASICO", ANA, BRUNO, partida()),
                        "un combatiente que no se le mando"));
    }

    @Test
    @DisplayName("las rutas que usa este cliente estan en el contrato del motor")
    void lasRutasEstanEnElContrato() throws IOException {
        // Desde el directorio del servicio, la raiz del repositorio esta tres
        // niveles arriba: salas-partidas -> plataforma -> services -> raiz.
        Path contrato = Path.of("..", "..", "..", "contracts", "openapi", "motor-combate.yaml");
        String yaml = Files.readString(contrato, StandardCharsets.UTF_8);

        assertAll(
                () -> assertTrue(yaml.contains("/api/v1/combate/acciones:"), "falta /combate/acciones"),
                () -> assertTrue(yaml.contains("/api/v1/combate/turnos:"), "falta /combate/turnos"),
                () -> assertTrue(yaml.contains(MotorDeCombate.DECISION_DE_LA_MAQUINA)),
                () -> assertTrue(yaml.contains("accion-no-permitida")));
    }
}
