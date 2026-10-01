package nexus.misiones;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import nexus.misiones.aplicacion.TrabajoDeMisiones;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * El servicio entero: seguridad real (tokens RS256 contra un JWKS), MongoDB 8
 * en un contenedor, los siete clientes REST contra servidores HTTP falsos y
 * cada respuesta validada contra contracts/openapi/misiones.yaml (11.4.2: «los
 * contratos entre servicios se cumplen»).
 *
 * <p>Una hora de mision dura aqui un milisegundo ({@code misiones.segundos-por-hora})
 * y el trabajo en segundo plano no corre solo: la prueba lo ejecuta cuando le
 * toca, que es lo que hace el programador en produccion cada treinta segundos.
 */
@SpringBootTest(properties = {
        "misiones.trabajo.activo=false",
        "misiones.semilla-provisional=true",
        "misiones.segundos-por-hora=0.001",
        "misiones.semilla-de-pruebas=7",
        "misiones.reintento-segundos=0",
        "resiliencia.fallos-para-abrir=1000",
        "seguridad.servicio.client-id=misiones",
        "seguridad.servicio.client-secret=secreto-de-prueba"
})
@AutoConfigureMockMvc
@Testcontainers
class ServicioDeMisionesIT {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGODB = new MongoDBContainer("mongo:8.0");

    private static final EmisorDeTokensDePrueba EMISOR = EmisorDeTokensDePrueba.emisor();
    private static final DependenciasFalsas FALSAS = arrancar();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String CONTRATO = contrato();
    /**
     * El validador es estricto con las propiedades de mas; {@code allOf} (el
     * detalle de una mision es el resumen MAS su detalle) se resuelve en un
     * solo esquema antes de validar, o cada mitad veria como «de mas» las
     * propiedades de la otra.
     */
    private static final OpenApiInteractionValidator VALIDADOR = OpenApiInteractionValidator.createFor(CONTRATO)
            .withResolveCombinators(true)
            .build();
    /** Para las peticiones que violan el contrato a proposito: solo se valida la respuesta. */
    private static final OpenApiInteractionValidator SOLO_RESPUESTA = OpenApiInteractionValidator.createFor(CONTRATO)
            .withResolveCombinators(true)
            .withLevelResolver(LevelResolver.create()
                    .withLevel("validation.request", ValidationReport.Level.IGNORE)
                    .build())
            .build();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TrabajoDeMisiones trabajo;

    private String jugador;
    private String token;
    private String heroeId;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
        registro.add("misiones.inventario.url", FALSAS::base);
        registro.add("misiones.productos.url", FALSAS::base);
        registro.add("misiones.heroes.url", FALSAS::base);
        registro.add("misiones.motor.url", FALSAS::base);
        registro.add("misiones.creditos.url", () -> FALSAS.base() + "/finanzas/api/v1");
        registro.add("misiones.correo.url", () -> FALSAS.base() + "/correo/api/v1");
        registro.add("misiones.notificaciones.url", () -> FALSAS.base() + "/notificaciones/api/v1");
        registro.add("misiones.identidad.url", () -> FALSAS.base() + "/identidad");
        registro.add("seguridad.servicio.url", () -> FALSAS.base() + "/auth/token");
    }

    @AfterAll
    static void cerrar() {
        FALSAS.close();
    }

    @BeforeEach
    void jugadorNuevo() {
        FALSAS.reiniciar();
        UUID uid = UUID.randomUUID();
        jugador = uid.toString();
        token = "Bearer " + EMISOR.tokenDeJugador("lyra", uid);
        heroeId = "heroe-" + UUID.randomUUID();
        FALSAS.conHeroe(heroeId, jugador, "producto-armas", "Guerrero Armas");
    }

    // ------------------------------------------------------------- el ciclo

    @Test
    @DisplayName("listar, preparar la estrategia, enviar al heroe, simular, liquidar y ver el reporte")
    void cicloCompleto() throws Exception {
        MvcResult tablon = mvc.perform(conToken(get("/api/v1/misiones").param("categoria", "HISTORIA")))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(VALIDADOR))
                .andReturn();
        assertThat(cuerpo(tablon).get("misiones").toString()).contains("templo-olvidado", "la-forja-sumergida", "dev-prueba-de-humo");

        mvc.perform(conToken(get("/api/v1/misiones/destacadas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("templo-olvidado"))
                .andExpect(openApi().isValid(VALIDADOR));

        mvc.perform(conToken(get("/api/v1/misiones/{id}", "templo-olvidado")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("El Templo Olvidado"))
                .andExpect(jsonPath("$.masters[0].nombre").value("Sombra del Olvido"))
                .andExpect(jsonPath("$.mastersPorTipoDeHeroe.length()").value(8))
                .andExpect(jsonPath("$.recompensas.primeraVez[1]").value("Título «Explorador del Templo»"))
                .andExpect(openApi().isValid(VALIDADOR));

        mvc.perform(conToken(put("/api/v1/misiones/estrategias/{heroe}", heroeId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rotaciones\":[{\"pasos\":[\"Embate sangriento\",\"Ataque básico\"]}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rotaciones[0].prioridad").value("Alta"))
                .andExpect(openApi().isValid(VALIDADOR));
        mvc.perform(conToken(get("/api/v1/misiones/estrategias/{heroe}", heroeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prototipo").value("Guerrero Armas"))
                .andExpect(openApi().isValid(VALIDADOR));

        // Matricula con la estrategia guardada (sin rotaciones en el cuerpo).
        MvcResult matricula = mvc.perform(conToken(post("/api/v1/misiones/{id}/ejecuciones", "dev-prueba-de-humo"))
                        .header("Idempotency-Key", "matricula-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"heroeId\":\"" + heroeId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("EN_PROGRESO"))
                .andExpect(openApi().isValid(VALIDADOR))
                .andReturn();
        String ejecucionId = (String) cuerpo(matricula).get("ejecucionId");

        mvc.perform(conToken(post("/api/v1/misiones/{id}/ejecuciones", "dev-prueba-de-humo"))
                        .header("Idempotency-Key", "matricula-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"heroeId\":\"" + heroeId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ejecucionId").value(ejecucionId))
                .andExpect(openApi().isValid(VALIDADOR));

        DependenciasFalsas.Peticion bloqueo = FALSAS.a("PUT", "/api/v1/inventario/elementos/" + heroeId).getFirst();
        assertThat(bloqueo.cabecera("Authorization")).isEqualTo("Bearer " + DependenciasFalsas.TOKEN_DE_SERVICIO);
        assertThat(bloqueo.cabecera("Idempotency-Key")).isEqualTo("mision-" + ejecucionId + "-bloqueo");
        assertThat(bloqueo.cabecera("traceparent")).as("regla 5: la traza viaja").isNotBlank();
        assertThat(bloqueo.json()).containsEntry("propietarioUid", jugador).containsEntry("ejecucionId", ejecucionId);
        assertThat(FALSAS.a("PUT", "/api/v1/inventario/elementos/")).hasSize(1);

        mvc.perform(conToken(get("/api/v1/misiones/en-curso")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ejecucionId").value(ejecucionId))
                .andExpect(openApi().isValid(VALIDADOR));
        mvc.perform(conToken(get("/api/v1/misiones/ejecuciones/{id}", ejecucionId)))
                .andExpect(status().isConflict())
                .andExpect(openApi().isValid(VALIDADOR));

        Thread.sleep(20);
        trabajo.ejecutar();

        MvcResult reporte = mvc.perform(conToken(get("/api/v1/misiones/ejecuciones/{id}", ejecucionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("EXITO"))
                .andExpect(jsonPath("$.jefeDerrotado").value(true))
                .andExpect(jsonPath("$.recompensas.creditos").value(5))
                .andExpect(jsonPath("$.recompensas.entregaPendiente").value(false))
                .andExpect(openApi().isValid(VALIDADOR))
                .andReturn();
        double experiencia = ((Number) ((Map<?, ?>) cuerpo(reporte).get("recompensas")).get("experiencia"))
                .doubleValue();
        assertThat(experiencia).as("dos enemigos: 10 x 1,2^(1d8) cada uno").isBetween(24.0, 86.0);

        DependenciasFalsas.Peticion liberacion = FALSAS.a("POST", "/api/v1/inventario/elementos/" + heroeId)
                .getFirst();
        assertThat(liberacion.ruta()).endsWith("/bloqueo-mision/" + ejecucionId + "/liberacion");
        assertThat(((Number) liberacion.json().get("experiencia")).doubleValue()).isEqualTo(experiencia);
        DependenciasFalsas.Peticion credito = FALSAS.con("POST", "/finanzas/", ejecucionId).getFirst();
        assertThat(credito.json()).containsEntry("uid", jugador).containsEntry("monto", 5)
                .containsEntry("refId", "mision-" + ejecucionId).containsEntry("concepto", "recompensa-mision");
        DependenciasFalsas.Peticion correo = FALSAS.con("POST", "/correo/", ejecucionId).getFirst();
        assertThat(correo.cabecera("Idempotency-Key")).isEqualTo("mision-" + ejecucionId + "-correo");
        assertThat(correo.json()).containsEntry("email", "jugador@ejemplo.com")
                .containsEntry("debeEnviarCorreo", true);
        // RF-NOT-004: el aviso en la bandeja, con la credencial de servicio de misiones.
        DependenciasFalsas.Peticion aviso = FALSAS.con("POST", "/notificaciones/", ejecucionId).getFirst();
        assertThat(aviso.cabecera("Authorization")).isEqualTo("Bearer " + DependenciasFalsas.TOKEN_DE_SERVICIO);
        assertThat(aviso.json()).containsEntry("usuarioId", jugador)
                .containsEntry("id", "mision-" + ejecucionId + "-aviso")
                .containsEntry("tipo", "MISION");
        assertThat((String) aviso.json().get("titulo")).contains("terminó con éxito");
        assertThat((String) aviso.json().get("cuerpo")).contains("Ganó 5 créditos");

        // Otra vuelta del trabajo no repite nada: todo quedo HECHO.
        long antes = FALSAS.cuantasCon(ejecucionId);
        trabajo.ejecutar();
        assertThat(FALSAS.cuantasCon(ejecucionId)).isEqualTo(antes);

        mvc.perform(conToken(get("/api/v1/misiones/historial")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completadas[0].ejecucionId").value(ejecucionId))
                .andExpect(jsonPath("$.completadas[0].resultado").value("EXITO"))
                .andExpect(openApi().isValid(VALIDADOR));
        mvc.perform(conToken(get("/api/v1/misiones").param("categoria", "HISTORIA").param("estado", "COMPLETADA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.misiones[0].id").value("dev-prueba-de-humo"))
                .andExpect(jsonPath("$.misiones[0].ultimaEjecucionId").value(ejecucionId))
                .andExpect(openApi().isValid(VALIDADOR));
    }

    @Test
    @DisplayName("HU-MIS-012: «La Forja Sumergida» esta en el tablon de Historia, bloqueada hasta completar el Templo, y su detalle cumple el contrato")
    void laForjaSumergida() throws Exception {
        mvc.perform(conToken(get("/api/v1/misiones").param("categoria", "HISTORIA").param("dificultad", "DIFICIL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.misiones.length()").value(1))
                .andExpect(jsonPath("$.misiones[0].id").value("la-forja-sumergida"))
                .andExpect(jsonPath("$.misiones[0].origen").value("EQUIPO"))
                .andExpect(jsonPath("$.misiones[0].estado").value("BLOQUEADA"))
                .andExpect(jsonPath("$.misiones[0].destacada").value(false))
                .andExpect(openApi().isValid(VALIDADOR));

        mvc.perform(conToken(get("/api/v1/misiones/{id}", "la-forja-sumergida")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("La Forja Sumergida"))
                .andExpect(jsonPath("$.origen").value("EQUIPO"))
                .andExpect(jsonPath("$.dificultad").value("DIFICIL"))
                .andExpect(jsonPath("$.requisitosPrevios[0]").value("El Templo Olvidado"))
                .andExpect(jsonPath("$.jefe.nombre").value("El Herrero Ahogado"))
                .andExpect(jsonPath("$.enemigos.length()").value(3))
                .andExpect(jsonPath("$.masters[0].nombre").value("Hija de la Escarcha"))
                .andExpect(jsonPath("$.masters[0].probabilidad").value(0.2))
                .andExpect(jsonPath("$.masters[0].epica.efectoGeneral").value("-1 de poder al oponente"))
                .andExpect(jsonPath("$.masters[0].epica.efectoPotenciado")
                        .value("No recibe ningún daño en el siguiente turno"))
                .andExpect(jsonPath("$.recompensas.primeraVez[1]").value("Título «Forjador del Lago»"))
                .andExpect(openApi().isValid(VALIDADOR));

        mvc.perform(conToken(get("/api/v1/misiones/destacadas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("templo-olvidado"))
                .andExpect(openApi().isValid(VALIDADOR));
    }

    @Test
    @DisplayName("cancelar libera al heroe sin experiencia; una mision abandonada no tiene reporte")
    void cancelar() throws Exception {
        String ejecucionId = matricular("templo-olvidado",
                "{\"heroeId\":\"" + heroeId + "\",\"rotaciones\":[{\"pasos\":[\"Embate sangriento\"]}]}");

        mvc.perform(conToken(post("/api/v1/misiones/ejecuciones/{id}/cancelacion", ejecucionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ABANDONADA"))
                .andExpect(jsonPath("$.heroeLiberado").value(true))
                .andExpect(openApi().isValid(VALIDADOR));

        assertThat(FALSAS.heroes.get(heroeId).bloqueadoPor).isNull();
        DependenciasFalsas.Peticion liberacion = FALSAS.a("POST", "/api/v1/inventario/elementos/" + heroeId)
                .getFirst();
        assertThat(((Number) liberacion.json().get("experiencia")).doubleValue()).isZero();
        mvc.perform(conToken(get("/api/v1/misiones/ejecuciones/{id}", ejecucionId)))
                .andExpect(status().isConflict())
                .andExpect(openApi().isValid(VALIDADOR));
        mvc.perform(conToken(post("/api/v1/misiones/ejecuciones/{id}/cancelacion", ejecucionId)))
                .andExpect(status().isConflict())
                .andExpect(openApi().isValid(VALIDADOR));
    }

    @Test
    @DisplayName("si el inventario no contesta al liberar, la entrega queda pendiente y se completa en otra vuelta")
    void entregaPendiente() throws Exception {
        String ejecucionId = matricular("dev-prueba-de-humo", "{\"heroeId\":\"" + heroeId + "\"}");
        Thread.sleep(20);
        // Solo la liberacion: simular al heroe ya pide sus estadisticas y su equipo al inventario.
        FALSAS.caidas.add("liberacion");
        trabajo.ejecutar();

        mvc.perform(conToken(get("/api/v1/misiones/ejecuciones/{id}", ejecucionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recompensas.entregaPendiente").value(true))
                .andExpect(openApi().isValid(VALIDADOR));
        assertThat(FALSAS.con("POST", "/finanzas/", ejecucionId)).as("la liberacion va primero y corta la vuelta")
                .isEmpty();

        FALSAS.caidas.clear();
        trabajo.ejecutar();

        mvc.perform(conToken(get("/api/v1/misiones/ejecuciones/{id}", ejecucionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recompensas.entregaPendiente").value(false))
                .andExpect(jsonPath("$.heroe.nivelAlcanzado").value(1))
                .andExpect(openApi().isValid(VALIDADOR));
        assertThat(FALSAS.con("POST", "/finanzas/", ejecucionId)).hasSize(1);
    }

    @Test
    @DisplayName("sin contacto en identidad el correo se da por fallido y el resto se entrega")
    void sinContacto() throws Exception {
        FALSAS.correoDelJugador = null;
        String ejecucionId = matricular("dev-prueba-de-humo", "{\"heroeId\":\"" + heroeId + "\"}");
        Thread.sleep(20);
        trabajo.ejecutar();

        mvc.perform(conToken(get("/api/v1/misiones/ejecuciones/{id}", ejecucionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recompensas.entregaPendiente").value(false))
                .andExpect(openApi().isValid(VALIDADOR));
        assertThat(FALSAS.con("POST", "/correo/", ejecucionId)).isEmpty();
        assertThat(FALSAS.con("POST", "/finanzas/", ejecucionId)).hasSize(1);
    }

    // ----------------------------------------------------------- rechazos

    @Test
    @DisplayName("matricula: mision inexistente, heroe ajeno, heroe ocupado y la misma mision dos veces")
    void rechazosDeMatricula() throws Exception {
        mvc.perform(matricula("no-existe", "{\"heroeId\":\"" + heroeId + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(openApi().isValid(VALIDADOR));

        FALSAS.conHeroe("ajeno", UUID.randomUUID().toString(), "producto-armas", "Guerrero Armas");
        mvc.perform(matricula("dev-prueba-de-humo", "{\"heroeId\":\"ajeno\"}"))
                .andExpect(status().isNotFound())
                .andExpect(openApi().isValid(VALIDADOR));

        FALSAS.heroes.get(heroeId).subastaId = UUID.randomUUID().toString();
        mvc.perform(matricula("dev-prueba-de-humo", "{\"heroeId\":\"" + heroeId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("subasta")))
                .andExpect(openApi().isValid(VALIDADOR));
        FALSAS.heroes.get(heroeId).subastaId = null;

        matricular("dev-prueba-de-humo", "{\"heroeId\":\"" + heroeId + "\"}");
        FALSAS.conHeroe("segundo-" + heroeId, jugador, "producto-armas", "Guerrero Armas");
        mvc.perform(matricula("dev-prueba-de-humo", "{\"heroeId\":\"segundo-" + heroeId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(openApi().isValid(VALIDADOR));
        mvc.perform(matricula("templo-olvidado", "{\"heroeId\":\"" + heroeId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("misión")))
                .andExpect(openApi().isValid(VALIDADOR));
    }

    @Test
    @DisplayName("matricula: un heroe no apto da todos sus motivos y una estrategia invalida sus habilidades")
    void heroeNoAptoYEstrategiaInvalida() throws Exception {
        FALSAS.conHeroe("chaman", jugador, "producto-chaman", "Chamán").equipado = false;
        mvc.perform(matricula("dev-prueba-de-humo", "{\"heroeId\":\"chaman\"}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.motivos.length()").value(2))
                .andExpect(openApi().isValid(VALIDADOR));

        mvc.perform(matricula("dev-prueba-de-humo",
                        "{\"heroeId\":\"" + heroeId + "\",\"rotaciones\":[{\"pasos\":[\"Vulcano\"]}]}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.habilidadesValidas[0]").value("Embate sangriento"))
                .andExpect(openApi().isValid(VALIDADOR));
        mvc.perform(conToken(put("/api/v1/misiones/estrategias/{heroe}", heroeId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rotaciones\":[{\"pasos\":[\"Vulcano\"]}]}"))
                .andExpect(status().is(422))
                .andExpect(openApi().isValid(VALIDADOR));
        assertThat(FALSAS.a("PUT", "/api/v1/inventario/")).as("no se bloquea a nadie").isEmpty();
    }

    @Test
    @DisplayName("escalones: Heroico sin completar Normal y Mitico sin cifra del PO no se ofrecen")
    void escalones() throws Exception {
        mvc.perform(matricula("dev-prueba-de-humo", "{\"heroeId\":\"" + heroeId + "\",\"escalon\":\"HEROICO\"}"))
                .andExpect(status().isConflict())
                .andExpect(openApi().isValid(VALIDADOR));
        mvc.perform(matricula("dev-prueba-de-humo", "{\"heroeId\":\"" + heroeId + "\",\"escalon\":\"MITICO\"}"))
                .andExpect(status().isConflict())
                .andExpect(openApi().isValid(VALIDADOR));
        mvc.perform(conToken(get("/api/v1/misiones/{id}", "dev-prueba-de-humo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escalones[0].desbloqueado").value(true))
                .andExpect(jsonPath("$.escalones[1].desbloqueado").value(false))
                .andExpect(jsonPath("$.escalones[3].multiplicadorDeEstadisticas").doesNotExist())
                .andExpect(openApi().isValid(VALIDADOR));
    }

    @Test
    @DisplayName("una dependencia caida es la seccion limitada de HU-DIS-003, sin nada a medias")
    void dependenciaCaida() throws Exception {
        FALSAS.caidas.add("productos");
        mvc.perform(matricula("dev-prueba-de-humo", "{\"heroeId\":\"" + heroeId + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/seccion-no-disponible"))
                .andExpect(openApi().isValid(VALIDADOR));
        assertThat(FALSAS.a("PUT", "/api/v1/inventario/")).isEmpty();
        mvc.perform(conToken(get("/api/v1/misiones/en-curso")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("peticiones fuera del contrato: 400 con problem detail")
    void solicitudesInvalidas() throws Exception {
        mvc.perform(matricula("dev-prueba-de-humo", "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(SOLO_RESPUESTA));
        mvc.perform(matricula("dev-prueba-de-humo", "{\"heroeId\":\"" + heroeId + "\",\"escalon\":\"RARO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(SOLO_RESPUESTA));
        mvc.perform(conToken(get("/api/v1/misiones").param("categoria", "RARA")))
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(SOLO_RESPUESTA));
        mvc.perform(conToken(get("/api/v1/misiones")))
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(SOLO_RESPUESTA));
        mvc.perform(conToken(get("/api/v1/misiones").param("categoria", "HISTORIA").param("pagina", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(SOLO_RESPUESTA));
        mvc.perform(conToken(post("/api/v1/misiones/{id}/ejecuciones", "dev-prueba-de-humo"))
                        .header("Idempotency-Key", "x".repeat(101))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"heroeId\":\"" + heroeId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(SOLO_RESPUESTA));
    }

    @Test
    @DisplayName("favoritas, estrategia sin guardar y ejecuciones ajenas o inexistentes")
    void favoritasYAjenas() throws Exception {
        mvc.perform(conToken(put("/api/v1/misiones/{id}/favorita", "templo-olvidado")))
                .andExpect(status().isNoContent())
                .andExpect(openApi().isValid(VALIDADOR));
        mvc.perform(conToken(get("/api/v1/misiones").param("categoria", "HISTORIA")))
                .andExpect(jsonPath("$.misiones[?(@.id=='templo-olvidado')].favorita").value(true));
        mvc.perform(conToken(delete("/api/v1/misiones/{id}/favorita", "templo-olvidado")))
                .andExpect(status().isNoContent())
                .andExpect(openApi().isValid(VALIDADOR));
        mvc.perform(conToken(put("/api/v1/misiones/{id}/favorita", "no-existe")))
                .andExpect(status().isNotFound())
                .andExpect(openApi().isValid(VALIDADOR));
        mvc.perform(conToken(get("/api/v1/misiones/{id}", "no-existe")))
                .andExpect(status().isNotFound())
                .andExpect(openApi().isValid(VALIDADOR));

        mvc.perform(conToken(get("/api/v1/misiones/estrategias/{heroe}", heroeId)))
                .andExpect(status().isNotFound())
                .andExpect(openApi().isValid(VALIDADOR));

        String ejecucionId = matricular("dev-prueba-de-humo", "{\"heroeId\":\"" + heroeId + "\"}");
        String otro = "Bearer " + EMISOR.tokenDeJugador("otro", UUID.randomUUID());
        mvc.perform(get("/api/v1/misiones/ejecuciones/{id}", ejecucionId).header("Authorization", otro))
                .andExpect(status().isNotFound())
                .andExpect(openApi().isValid(VALIDADOR));
        mvc.perform(post("/api/v1/misiones/ejecuciones/{id}/cancelacion", ejecucionId).header("Authorization", otro))
                .andExpect(status().isNotFound())
                .andExpect(openApi().isValid(VALIDADOR));
        mvc.perform(conToken(get("/api/v1/misiones/ejecuciones/{id}", "no-es-un-uuid")))
                .andExpect(status().isNotFound())
                .andExpect(openApi().isValid(SOLO_RESPUESTA));
    }

    @Test
    @DisplayName("seguridad: sin token 401, con token de servicio 403, el actuator abierto")
    void seguridad() throws Exception {
        mvc.perform(get("/api/v1/misiones").param("categoria", "HISTORIA"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/misiones").param("categoria", "HISTORIA")
                        .header("Authorization", "Bearer " + EMISOR.tokenDeServicio("salas-partidas")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/misiones").param("categoria", "HISTORIA")
                        .header("Authorization", "Bearer " + EMISOR.tokenCaducado("lyra", UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mvc.perform(conToken(get("/otra/ruta")))
                .andExpect(status().isForbidden());
    }

    // --------------------------------------------------------------- apoyo

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion) {
        return peticion.header("Authorization", token);
    }

    private MockHttpServletRequestBuilder matricula(String mision, String cuerpo) {
        return conToken(post("/api/v1/misiones/{id}/ejecuciones", mision))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private String matricular(String mision, String cuerpo) throws Exception {
        MvcResult resultado = mvc.perform(matricula(mision, cuerpo))
                .andExpect(status().isCreated())
                .andExpect(openApi().isValid(VALIDADOR))
                .andReturn();
        return (String) cuerpo(resultado).get("ejecucionId");
    }

    private static Map<String, Object> cuerpo(MvcResult resultado) throws Exception {
        return JSON.readValue(resultado.getResponse().getContentAsString(), Map.class);
    }

    private static DependenciasFalsas arrancar() {
        try {
            return new DependenciasFalsas();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String contrato() {
        String ruta = System.getProperty("misiones.contrato", "../../../contracts/openapi/misiones.yaml");
        Path contrato = Path.of(ruta);
        if (!Files.exists(contrato)) {
            throw new IllegalStateException("No se encuentra el contrato de misiones en " + contrato.toAbsolutePath());
        }
        return contrato.toUri().toString();
    }

}
