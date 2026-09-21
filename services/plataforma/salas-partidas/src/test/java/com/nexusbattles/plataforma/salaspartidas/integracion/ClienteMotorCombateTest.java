package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResolucionDelMotor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * El cliente que de verdad habla con el motor de combate.
 *
 * <p><b>Por que existe esta prueba.</b> Todo el combate se probaba con el doble
 * {@code MotorDeMentira}: {@code EjecutarAccionTest} y {@code TurnoDeLaMaquinaTest}
 * lo usan, y ninguno toca esta clase. O sea que el unico codigo que se ejecuta
 * de verdad cuando un jugador ataca en el servidor no lo habia ejercitado nunca
 * nada. Un nombre de campo mal escrito en el JSON, un error mal traducido o una
 * URI equivocada pasaban la suite entera en verde y reventaban en la demo.
 *
 * <p>Aqui el transporte HTTP esta simulado, pero lo que importa NO lo esta: la
 * serializacion de Jackson, la URI que se compone, el mapeo de la respuesta y la
 * traduccion de errores son los reales.
 */
@DisplayName("ClienteMotorCombate · el adaptador HTTP que corre en produccion")
class ClienteMotorCombateTest {

    private static final String BASE = "http://srv-motor-combate:8080";

    private MockRestServiceServer motor;
    private ClienteMotorCombate cliente;

    private static HeroeDeCombate arquero() {
        return new HeroeDeCombate("h-1", "Arquero del Norte", null, 5, 100, 100);
    }

    private static HeroeDeCombate mago(int vidaActual) {
        return new HeroeDeCombate("h-2", "Mago de Hielo", null, 4, vidaActual, 120);
    }

    @BeforeEach
    void prepararElMotor() {
        RestClient.Builder constructor = RestClient.builder();
        motor = MockRestServiceServer.bindTo(constructor).build();
        cliente = new ClienteMotorCombate(constructor.build(), BASE);
    }

    // =====================================================================
    // Lo que se manda
    // =====================================================================

    @Test
    @DisplayName("pide el ataque a la ruta del contrato, con el cuerpo del contrato")
    void mandaLoQueElContratoDice() {
        motor.expect(requestTo(BASE + "/api/v1/combate/ataques"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.heroeAtacante").value("Arquero del Norte"))
                .andExpect(jsonPath("$.defensaObjetivo").value(90))
                .andExpect(jsonPath("$.distribucion.prototipo").value("GUERRERO_ARMAS"))
                .andRespond(withSuccess(
                        """
                        {"categoria":"CAUSAR_DANO","danoAplicado":17,"ataqueResuelto":42,
                         "defensaObjetivo":90,"indiceTabla":3}
                        """,
                        MediaType.APPLICATION_JSON));

        cliente.resolver(arquero(), mago(90));

        motor.verify();
    }

    @Test
    @DisplayName("manda el PROTOTIPO del heroe, no el nombre que le puso su dueno")
    void mandaElPrototipoYNoElNombrePropio() {
        // El defecto que cierra, destapado por el E2E del corte vertical: el
        // motor resuelve al atacante contra GET /api/v1/heroes/{nombre}, que
        // indexa por prototipo. Mandandole «Aquiles» —el nombre propio— el
        // catalogo devolvia 404 y NINGUN ataque se resolvia; el error se iba a
        // la cola privada del jugador, que la vista no escucha, asi que el
        // combate se quedaba quieto sin decir nada.
        HeroeDeCombate aquiles = new HeroeDeCombate(
                "h-9", "Aquiles", "Guerrero Tanque", null, 5, 100, 100);

        motor.expect(requestTo(BASE + "/api/v1/combate/ataques"))
                .andExpect(jsonPath("$.heroeAtacante").value("Guerrero Tanque"))
                .andRespond(withSuccess(
                        "{\"categoria\":\"CAUSAR_DANO\",\"danoAplicado\":5,\"ataqueResuelto\":20}",
                        MediaType.APPLICATION_JSON));

        cliente.resolver(aquiles, mago(90));

        motor.verify();
    }

    @Test
    @DisplayName("sin prototipo conocido cae al nombre: degradar, no callarse")
    void sinPrototipoCaeAlNombre() {
        // Fichas anteriores a V8, o productos sin contestar. Se manda lo que
        // hay: funciona si el heroe se llama como su prototipo y falla igual
        // que antes si no. Fijado para que el dia que deje de hacer falta, se
        // quite a proposito.
        motor.expect(requestTo(BASE + "/api/v1/combate/ataques"))
                .andExpect(jsonPath("$.heroeAtacante").value("Arquero del Norte"))
                .andRespond(withSuccess(
                        "{\"categoria\":\"CAUSAR_DANO\",\"danoAplicado\":5,\"ataqueResuelto\":20}",
                        MediaType.APPLICATION_JSON));

        cliente.resolver(arquero(), mago(90));

        motor.verify();
    }

    @Test
    @DisplayName("manda la vida actual del objetivo como defensa: la simplificacion queda fijada")
    void laDefensaEsLaVidaActual() {
        // No es una regla acordada en ninguna HU —esta anotada como pendiente en
        // el javadoc de la clase—, pero es lo que hace hoy y tiene consecuencia
        // observable: un heroe herido se defiende peor. Se fija para que el dia
        // que cambie, cambie a proposito.
        motor.expect(requestTo(BASE + "/api/v1/combate/ataques"))
                .andExpect(jsonPath("$.defensaObjetivo").value(35))
                .andRespond(withSuccess(
                        "{\"categoria\":\"SIN_EFECTO\",\"danoAplicado\":0,\"ataqueResuelto\":8}",
                        MediaType.APPLICATION_JSON));

        cliente.resolver(arquero(), mago(35));

        motor.verify();
    }

    @Test
    @DisplayName("el prototipo por defecto es uno de los seis que publica el contrato del motor")
    void elPrototipoExisteEnElContrato() throws IOException {
        // Prueba de contrato de verdad: si alguien renombra un prototipo en
        // motor-combate, esto se pone rojo aqui. Sin ella, el motor devolveria
        // 400 "Prototipo desconocido", el cliente lo traduciria a
        // MotorNoDisponible y TODOS los ataques fallarian en silencio.
        List<String> delContrato = prototiposDelContrato();

        assertAll(
                () -> assertEquals(6, delContrato.size(),
                        "el contrato dice seis prototipos: " + delContrato),
                () -> assertTrue(delContrato.contains(ClienteMotorCombate.PROTOTIPO_POR_DEFECTO),
                        ClienteMotorCombate.PROTOTIPO_POR_DEFECTO
                                + " no esta entre los del contrato: " + delContrato));
    }

    // =====================================================================
    // Lo que se recibe
    // =====================================================================

    @Test
    @DisplayName("traduce la respuesta del motor a la resolucion del dominio")
    void traduceLaRespuesta() {
        motor.expect(requestTo(BASE + "/api/v1/combate/ataques"))
                .andRespond(withSuccess(
                        "{\"categoria\":\"CAUSAR_DANO_CRITICO\",\"danoAplicado\":34,"
                                + "\"ataqueResuelto\":57,\"defensaObjetivo\":90,\"indiceTabla\":1}",
                        MediaType.APPLICATION_JSON));

        ResolucionDelMotor resolucion = cliente.resolver(arquero(), mago(90));

        assertAll(
                () -> assertEquals("CAUSAR_DANO_CRITICO", resolucion.categoria()),
                () -> assertEquals(34, resolucion.danoAplicado()),
                () -> assertEquals(57, resolucion.ataqueResuelto()));
    }

    @Test
    @DisplayName("un campo nuevo en la respuesta del motor no rompe el combate")
    void toleraCamposQueNoConoce() {
        // El motor puede anadir campos sin romper a sus clientes: eso es lo que
        // promete `@JsonIgnoreProperties(ignoreUnknown = true)`. Sin esta prueba,
        // quitarlo por descuido dejaria el combate caido en cuanto el otro equipo
        // ampliara su respuesta.
        motor.expect(requestTo(BASE + "/api/v1/combate/ataques"))
                .andRespond(withSuccess(
                        "{\"categoria\":\"CAUSAR_DANO\",\"danoAplicado\":12,\"ataqueResuelto\":30,"
                                + "\"campoQueAunNoExiste\":\"lo que sea\",\"otro\":{\"anidado\":1}}",
                        MediaType.APPLICATION_JSON));

        assertEquals(12, cliente.resolver(arquero(), mago(90)).danoAplicado());
    }

    @Test
    @DisplayName("un ataque que falla es 200 con dano cero, no un error")
    void elFalloEsUnResultadoLegitimo() {
        motor.expect(requestTo(BASE + "/api/v1/combate/ataques"))
                .andRespond(withSuccess(
                        "{\"categoria\":\"SIN_EFECTO\",\"danoAplicado\":0,\"ataqueResuelto\":11}",
                        MediaType.APPLICATION_JSON));

        ResolucionDelMotor resolucion = cliente.resolver(arquero(), mago(90));

        assertAll(
                () -> assertEquals("SIN_EFECTO", resolucion.categoria()),
                () -> assertEquals(0, resolucion.danoAplicado()));
    }

    // =====================================================================
    // Cuando el motor falla
    // =====================================================================

    @Test
    @DisplayName("un 5xx es motor no disponible, NO un dano de cero")
    void elErrorDelServidorNoSeConfundeConUnFallo() {
        // La diferencia importa: un cero se confunde con un ataque fallido y
        // decidiria el combate con un numero que nadie calculo.
        motor.expect(requestTo(BASE + "/api/v1/combate/ataques")).andRespond(withServerError());

        MotorNoDisponible error =
                assertThrows(MotorNoDisponible.class, () -> cliente.resolver(arquero(), mago(90)));

        assertEquals(503, error.estado());
    }

    @Test
    @DisplayName("un 400 del motor tambien es motor no disponible")
    void elErrorDePeticionTambienSePropaga() {
        motor.expect(requestTo(BASE + "/api/v1/combate/ataques"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"title\":\"Prototipo desconocido\"}"));

        assertThrows(MotorNoDisponible.class, () -> cliente.resolver(arquero(), mago(90)));
    }

    @Test
    @DisplayName("un cuerpo vacio no se toma por una resolucion")
    void elCuerpoVacioNoPasaPorBueno() {
        motor.expect(requestTo(BASE + "/api/v1/combate/ataques"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        MotorNoDisponible error =
                assertThrows(MotorNoDisponible.class, () -> cliente.resolver(arquero(), mago(90)));

        assertAll(
                () -> assertEquals(503, error.estado()),
                () -> assertTrue(error.detalle().contains("no se entiende"),
                        "el detalle tiene que decir que el cuerpo no se entendio: "
                                + error.detalle()));
    }

    @Test
    @DisplayName("una respuesta sin categoria no se toma por una resolucion")
    void elCuerpoSinCategoriaNoPasaPorBueno() {
        motor.expect(requestTo(BASE + "/api/v1/combate/ataques"))
                .andRespond(withSuccess("{\"danoAplicado\":20,\"ataqueResuelto\":40}",
                        MediaType.APPLICATION_JSON));

        assertThrows(MotorNoDisponible.class, () -> cliente.resolver(arquero(), mago(90)));
    }

    // =====================================================================

    /** Los nombres del enum `NombreDePrototipo` del contrato del motor. */
    private static List<String> prototiposDelContrato() throws IOException {
        // Desde el directorio del servicio, la raiz del repositorio esta tres
        // niveles arriba: salas-partidas -> plataforma -> services -> raiz.
        Path contrato = Path.of("..", "..", "..", "contracts", "openapi", "motor-combate.yaml");
        String yaml = Files.readString(contrato, StandardCharsets.UTF_8);

        Matcher bloque = Pattern
                .compile("NombreDePrototipo:.*?enum:(.*?)(?:\\n\\s{4}\\w|\\z)", Pattern.DOTALL)
                .matcher(yaml);
        if (!bloque.find()) {
            throw new AssertionError(
                    "No se encontro el enum NombreDePrototipo en " + contrato.toAbsolutePath());
        }
        return Pattern.compile("-\\s*([A-Z_]+)").matcher(bloque.group(1))
                .results().map(r -> r.group(1)).toList();
    }
}
