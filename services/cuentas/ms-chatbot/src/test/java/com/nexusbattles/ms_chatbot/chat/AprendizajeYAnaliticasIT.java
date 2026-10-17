package com.nexusbattles.ms_chatbot.chat;

import com.jayway.jsonpath.JsonPath;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import com.nexusbattles.ms_chatbot.chat.service.BrechaConocimientoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ms-chatbot.yaml 1.7.0 (7.4.5 y 7.4.7) contra PostgreSQL real con V1-V13: el
 * esquema de V13, las preguntas sin respuesta en el panel (ranking, filtros,
 * estado con quien y cuando, reapertura, 403) y las formulas del tablero con
 * un conjunto sembrado cuyas cifras se calculan a mano aqui mismo.
 */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@DisplayName("Chatbot · preguntas sin respuesta y analiticas (1.7.0, V13)")
class AprendizajeYAnaliticasIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final String UID_ADMIN = "7d1f1c1e-0000-4000-8000-00000000a0a0";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BrechaConocimientoService brechas;

    // La lista negra es otro servicio: aqui aprueba todo.
    @MockitoBean
    private ModeracionDeContenido moderacion;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM chatbot.conversaciones");
        jdbc.update("DELETE FROM chatbot.brechas_conocimiento");
        jdbc.update("DELETE FROM chatbot.tickets_soporte");
    }

    private static RequestPostProcessor administrador() {
        return jwt().jwt(j -> j.claim("uid", UID_ADMIN).claim("rol", "ADMINISTRADOR"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
    }

    // ------------------------------------------------------------------ V13

    @Test
    @DisplayName("V13: estado de la brecha con quien y cuando, resultado de la respuesta y sus restricciones")
    void v13AgregaEstadoYResultado() {
        List<String> columnas = jdbc.queryForList("""
            SELECT table_name || '.' || column_name FROM information_schema.columns
            WHERE table_schema = 'chatbot' AND column_name IN
              ('estado', 'estado_cambiado_por', 'estado_cambiado_en', 'reaperturas', 'resultado')
            """, String.class);
        assertThat(columnas).contains("brechas_conocimiento.estado", "brechas_conocimiento.estado_cambiado_por",
            "brechas_conocimiento.estado_cambiado_en", "brechas_conocimiento.reaperturas", "mensajes.resultado");

        // Una brecha nace PENDIENTE y sin reaperturas.
        jdbc.update("""
            INSERT INTO chatbot.brechas_conocimiento (id, texto_normalizado, ejemplo_pregunta, contador_no_util,
              contador_escalamiento, fecha_primera_ocurrencia, fecha_ultima_ocurrencia)
            VALUES (gen_random_uuid(), 'como gano gemas', 'Como gano gemas', 0, 1, now(), now())
            """);
        assertThat(jdbc.queryForMap("SELECT estado, reaperturas FROM chatbot.brechas_conocimiento"))
            .containsEntry("estado", "PENDIENTE").containsEntry("reaperturas", 0);

        assertThatThrownBy(() -> jdbc.update("UPDATE chatbot.brechas_conocimiento SET estado = 'RESUELTA'"))
            .isInstanceOf(DataIntegrityViolationException.class);
        UUID conversacion = conversacion("anonimo:v13", false, Instant.parse("2026-09-01T15:00:00Z"));
        assertThatThrownBy(() -> mensaje(conversacion, "BOT", "x", Instant.parse("2026-09-01T15:00:00Z"), false, 1,
            "RESUELTA", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname = 'chatbot' AND indexname IN "
            + "('ix_brechas_estado_ultima_ocurrencia', 'ix_conversaciones_fecha_inicio')", Integer.class))
            .isEqualTo(2);
    }

    // -------------------------------------------------------------- formulas

    /**
     * Periodo: 1 y 2 de septiembre en Bogota = [2026-09-01T05:00Z,
     * 2026-09-03T05:00Z). Periodo anterior: 30 y 31 de agosto.
     *
     * <pre>
     * Conversacion  inicio (Bogota)          en el periodo
     *   C1 jugador  01-sep 10:00            iniciada (dia 1), 2 preguntas
     *   C2 visit.   01-sep 22:00 (02 03Z)   iniciada (dia 1), 2 preguntas
     *   C3 jugador  20-ago                  no iniciada, 5 preguntas (dia 2)
     *   C4 visit.   02-sep 10:00            iniciada (dia 2), historial borrado
     *   C5 visit.   03-sep 00:00            fuera (fin exclusivo)
     *
     * Respuestas del bot (resultado, escalado, ms):
     *   C1  TEMA pujar-o-comprar, no, 100 | NO_DISPONIBLE, no, 1600
     *   C2  TEMA pujar-o-comprar, no, 120 | ESCALADO, si, 80
     *   C3  NAVEGACION, no, 50 | NO_SIRVIO, si, 30 | PIDE_REFORMULAR, no, 20
     *       | anterior a V13 (sin resultado), no, 200 | anterior a V4 (nada)
     *
     * A mano:
     *   conversaciones (iniciadas)            3 = C1, C2, C4
     *   conversacionesConActividad            3 = C1, C2, C3
     *   preguntas                             9 = 2 + 2 + 5
     *   respuestasMedidas (escalado no nulo)  8 = 2 + 2 + 4
     *   escalamientos                         2 = ESCALADO + NO_SIRVIO
     *   respuestasClasificadas                7 = las 9 menos la de antes de V13 y la de antes de V4
     *   respuestasResueltas                   3 = TEMA, TEMA, NAVEGACION
     *   tasaResolucion                        3 / 7
     *   tiempo promedio                     275 = (100+1600+120+80+50+30+20+200) / 8
     *   satisfaccion                        0.5 = 1 util de 2 en el periodo
     *   preguntasFrecuentes                 «como pujo en una subasta»: 3 veces, 3 conversaciones
     *   palabrasClave                       pujo 3 (NUEVA), subasta 3 (antes 1: SUBE)
     *   tendencia dia 1: 2 iniciadas, 4 preguntas, 4 medidas, 1 escalamiento
     *   tendencia dia 2: 1 iniciada,  5 preguntas, 4 medidas, 1 escalamiento
     * </pre>
     */
    @Test
    @DisplayName("las formulas del tablero dan lo que se calcula a mano sobre un conjunto sembrado")
    void lasFormulasDelTableroDanLoCalculadoAMano() throws Exception {
        UUID c1 = conversacion(UUID.randomUUID().toString(), true, Instant.parse("2026-09-01T15:00:00Z"));
        UUID c2 = conversacion("anonimo:c2", false, Instant.parse("2026-09-02T03:00:00Z"));
        UUID c3 = conversacion(UUID.randomUUID().toString(), true, Instant.parse("2026-08-20T15:00:00Z"));
        conversacion("anonimo:c4", false, Instant.parse("2026-09-02T15:00:00Z"));
        conversacion("anonimo:c5", false, Instant.parse("2026-09-03T05:00:00Z"));

        UUID temaC1 = intercambio(c1, "¿Cómo pujo en una subasta?", "2026-09-01T15:00:00Z", false, 100, "TEMA",
            "pujar-o-comprar");
        UUID noDisponible = intercambio(c1, "mis misiones", "2026-09-01T15:05:00Z", false, 1600, "NO_DISPONIBLE",
            null);
        intercambio(c2, "como pujo en una subasta", "2026-09-02T03:00:00Z", false, 120, "TEMA", "pujar-o-comprar");
        intercambio(c2, "xyzzy plugh", "2026-09-02T03:01:00Z", true, 80, "ESCALADO", null);
        intercambio(c3, "COMO PUJO EN UNA SUBASTA", "2026-09-02T15:00:00Z", false, 50, "NAVEGACION", null);
        intercambio(c3, "no me sirvió", "2026-09-02T15:01:00Z", true, 30, "NO_SIRVIO", null);
        intercambio(c3, "repítelo", "2026-09-02T15:02:00Z", false, 20, "PIDE_REFORMULAR", null);
        intercambio(c3, "=HYPERLINK(1)", "2026-09-02T16:00:00Z", false, 200, null, null);
        intercambio(c3, "hola", "2026-09-02T16:30:00Z", null, null, null, null);
        // Del periodo anterior: solo cuenta para la tendencia de las palabras.
        mensaje(c3, "USUARIO", "subasta cancelada", Instant.parse("2026-08-31T15:00:00Z"), null, null, null, null);
        calificacion(temaC1, true, "2026-09-01T15:10:00Z");
        calificacion(noDisponible, false, "2026-09-01T15:11:00Z");
        jdbc.update("""
            INSERT INTO chatbot.tickets_soporte (id, uid, categoria, asunto, mensaje, estado, creado_en, actualizado_en)
            VALUES (gen_random_uuid(), ?, 'SOPORTE_TECNICO', 'No carga', 'Detalle', 'ABIERTO', ?, ?)
            """, UUID.randomUUID().toString(), ts("2026-09-02T15:10:00Z"), ts("2026-09-02T15:10:00Z"));

        String cuerpo = mvc.perform(get("/chatbot/admin/analiticas").param("desde", "2026-09-01")
                .param("hasta", "2026-09-02").with(administrador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversaciones").value(3))
            .andExpect(jsonPath("$.conversacionesConActividad").value(3))
            .andExpect(jsonPath("$.preguntas").value(9))
            .andExpect(jsonPath("$.respuestasMedidas").value(8))
            .andExpect(jsonPath("$.escalamientos").value(2))
            .andExpect(jsonPath("$.respuestasClasificadas").value(7))
            .andExpect(jsonPath("$.respuestasResueltas").value(3))
            .andExpect(jsonPath("$.tasaResolucion").value(3.0 / 7))
            .andExpect(jsonPath("$.tiempoRespuestaPromedioMs").value(275.0))
            .andExpect(jsonPath("$.calificaciones").value(2))
            .andExpect(jsonPath("$.satisfaccion").value(0.5))
            .andExpect(jsonPath("$.tickets.total").value(1))
            .andExpect(jsonPath("$.temasFrecuentes[0].clave").value("pujar-o-comprar"))
            .andExpect(jsonPath("$.temasFrecuentes[0].respuestas").value(2))
            .andExpect(jsonPath("$.preguntasFrecuentes.length()").value(1))
            .andExpect(jsonPath("$.preguntasFrecuentes[0].texto").value("como pujo en una subasta"))
            .andExpect(jsonPath("$.preguntasFrecuentes[0].preguntas").value(3))
            .andExpect(jsonPath("$.preguntasFrecuentes[0].conversaciones").value(3))
            .andExpect(jsonPath("$.palabrasClave[0].palabra").value("pujo"))
            .andExpect(jsonPath("$.palabrasClave[0].tendencia").value("NUEVA"))
            .andExpect(jsonPath("$.palabrasClave[1].palabra").value("subasta"))
            .andExpect(jsonPath("$.palabrasClave[1].preguntas").value(3))
            .andExpect(jsonPath("$.palabrasClave[1].preguntasPeriodoAnterior").value(1))
            .andExpect(jsonPath("$.palabrasClave[1].tendencia").value("SUBE"))
            .andExpect(jsonPath("$.tendencia.length()").value(2))
            .andExpect(jsonPath("$.tendencia[0].conversaciones").value(2))
            .andExpect(jsonPath("$.tendencia[0].preguntas").value(4))
            .andExpect(jsonPath("$.tendencia[0].respuestas").value(4))
            .andExpect(jsonPath("$.tendencia[0].escalamientos").value(1))
            .andExpect(jsonPath("$.tendencia[1].conversaciones").value(1))
            .andExpect(jsonPath("$.tendencia[1].preguntas").value(5))
            .andExpect(jsonPath("$.tendencia[1].respuestas").value(4))
            .andExpect(jsonPath("$.tendencia[1].escalamientos").value(1))
            .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> resultados = JsonPath.read(cuerpo, "$.resultados");
        assertThat(resultados).extracting(r -> r.get("resultado") + "=" + r.get("respuestas")).containsExactly(
            "TEMA=2", "CONSULTA_EN_VIVO=0", "NAVEGACION=1", "NO_DISPONIBLE=1", "ESCALADO=1", "NO_SIRVIO=1",
            "PIDE_REFORMULAR=1");

        // El CSV del mismo periodo trae las cifras nuevas.
        mvc.perform(get("/chatbot/admin/analiticas/exportacion").param("desde", "2026-09-01")
                .param("hasta", "2026-09-02").with(administrador()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Conversaciones iniciadas;3")))
            .andExpect(content().string(containsString("Tasa de resolucion (%);42.9")))
            .andExpect(content().string(containsString("como pujo en una subasta;3;3")));

        // Borrar un historial no cambia las conversaciones iniciadas del pasado.
        jdbc.update("DELETE FROM chatbot.mensajes WHERE conversacion_id = ?", c1);
        mvc.perform(get("/chatbot/admin/analiticas").param("desde", "2026-09-01").param("hasta", "2026-09-02")
                .with(administrador()))
            .andExpect(jsonPath("$.conversaciones").value(3))
            .andExpect(jsonPath("$.conversacionesConActividad").value(2));
    }

    // ------------------------------------------------------------- brechas

    @Test
    @DisplayName("las preguntas sin respuesta salen las mas repetidas primero, por periodo, estado y pagina")
    void lasBrechasSeOrdenanYFiltran() throws Exception {
        brecha("como gano gemas", "¿Cómo gano gemas?", 1, 1, "2026-09-02T15:00:00Z", "2026-09-10T15:00:00Z",
            "PENDIENTE");
        brecha("donde esta el clan", "¿Dónde está el clan?", 5, 0, "2026-09-05T15:00:00Z", "2026-09-06T15:00:00Z",
            "PENDIENTE");
        brecha("que es el nexo", "¿Qué es el nexo?", 0, 2, "2026-09-03T15:00:00Z", "2026-09-04T15:00:00Z",
            "ATENDIDA");
        // Fuera de septiembre: la ultima vez fue en agosto.
        brecha("vieja", "vieja", 9, 9, "2026-08-01T15:00:00Z", "2026-08-02T15:00:00Z", "PENDIENTE");
        // Empezo antes y siguio despues: tuvo actividad en septiembre.
        brecha("larga", "larga", 3, 0, "2026-08-01T15:00:00Z", "2026-10-02T15:00:00Z", "DESCARTADA");

        mvc.perform(get("/chatbot/admin/brechas").param("desde", "2026-09-01").param("hasta", "2026-09-30")
                .with(administrador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(4))
            .andExpect(jsonPath("$.contenido[0].pregunta").value("¿Dónde está el clan?"))
            .andExpect(jsonPath("$.contenido[0].total").value(5))
            .andExpect(jsonPath("$.contenido[1].pregunta").value("larga"))
            // A igual total (2), la mas reciente primero.
            .andExpect(jsonPath("$.contenido[2].pregunta").value("¿Cómo gano gemas?"))
            .andExpect(jsonPath("$.contenido[3].pregunta").value("¿Qué es el nexo?"));

        mvc.perform(get("/chatbot/admin/brechas").param("desde", "2026-09-01").param("hasta", "2026-09-30")
                .param("estado", "PENDIENTE").param("pagina", "1").param("tamano", "1").with(administrador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(2))
            .andExpect(jsonPath("$.contenido.length()").value(1))
            .andExpect(jsonPath("$.contenido[0].pregunta").value("¿Cómo gano gemas?"));
    }

    @Test
    @DisplayName("marcar una brecha guarda quien (del token) y cuando; si se vuelve a preguntar, vuelve a PENDIENTE")
    void marcarYReabrir() throws Exception {
        brechas.registrarEscalamiento("¿Cómo gano gemas?");
        String id = jdbc.queryForObject("SELECT id::text FROM chatbot.brechas_conocimiento", String.class);

        mvc.perform(patch("/chatbot/admin/brechas/" + id).with(administrador())
                .contentType("application/json").content("{\"estado\":\"ATENDIDA\",\"estadoCambiadoPor\":\"otro\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("ATENDIDA"))
            .andExpect(jsonPath("$.estadoCambiadoPor").value(UID_ADMIN));
        Map<String, Object> marcada = jdbc.queryForMap(
            "SELECT estado, estado_cambiado_por, estado_cambiado_en, reaperturas FROM chatbot.brechas_conocimiento");
        assertThat(marcada).containsEntry("estado", "ATENDIDA").containsEntry("estado_cambiado_por", UID_ADMIN);
        assertThat(marcada.get("estado_cambiado_en")).isNotNull();

        // Alguien la vuelve a preguntar (otra forma, mismo texto normalizado).
        brechas.registrarEscalamiento("como gano GEMAS");

        mvc.perform(get("/chatbot/admin/brechas").param("estado", "PENDIENTE").with(administrador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido[0].estado").value("PENDIENTE"))
            .andExpect(jsonPath("$.contenido[0].reaperturas").value(1))
            .andExpect(jsonPath("$.contenido[0].escalamientos").value(2))
            .andExpect(jsonPath("$.contenido[0].estadoCambiadoPor").doesNotExist());

        mvc.perform(patch("/chatbot/admin/brechas/" + UUID.randomUUID()).with(administrador())
                .contentType("application/json").content("{\"estado\":\"DESCARTADA\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un jugador no ve ni marca las preguntas sin respuesta (403) y sin token es 401")
    void unJugadorNoVeLasBrechas() throws Exception {
        brechas.registrarEscalamiento("¿Cómo gano gemas?");
        String id = jdbc.queryForObject("SELECT id::text FROM chatbot.brechas_conocimiento", String.class);
        RequestPostProcessor jugador = jwt().jwt(j -> j.claim("uid", UUID.randomUUID().toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_JUGADOR"));

        mvc.perform(get("/chatbot/admin/brechas").with(jugador)).andExpect(status().isForbidden());
        mvc.perform(patch("/chatbot/admin/brechas/" + id).with(jugador)
                .contentType("application/json").content("{\"estado\":\"DESCARTADA\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/chatbot/admin/brechas")).andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("SELECT estado FROM chatbot.brechas_conocimiento", String.class))
            .isEqualTo("PENDIENTE");
    }

    // ------------------------------------------------ resultado de punta a punta

    @Test
    @DisplayName("el chat guarda como termino cada respuesta: escalada, no le sirvio, pide reformular, tema, no disponible")
    void elChatGuardaElResultadoDeCadaRespuesta() throws Exception {
        String sesion = "visitante-" + UUID.randomUUID();
        escribir(null, sesion, "zxqwv plorf");
        escribir(null, sesion, "no me sirvió");
        escribir(null, sesion, "repítelo");
        escribir(null, sesion, "¿Cómo pujo en una subasta?");
        // Un jugador pide sus misiones y el servicio no esta: no cuenta como resuelta.
        UUID jugador = UUID.randomUUID();
        escribir(jwt().jwt(j -> j.claim("uid", jugador.toString())), null, "mis misiones");

        List<String> resultados = jdbc.queryForList("""
            SELECT m.resultado FROM chatbot.mensajes m JOIN chatbot.conversaciones c ON c.id = m.conversacion_id
            WHERE m.remitente = 'BOT' ORDER BY c.autenticado, m.fecha_envio
            """, String.class);
        assertThat(resultados).containsExactly("ESCALADO", "NO_SIRVIO", "PIDE_REFORMULAR", "TEMA", "NO_DISPONIBLE");
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM chatbot.mensajes WHERE remitente = 'USUARIO' AND resultado IS NOT NULL",
            Integer.class)).isZero();
    }

    // ------------------------------------------------------------- auxiliares

    private void escribir(RequestPostProcessor quien, String sesion, String texto) throws Exception {
        var peticion = post("/chat/mensajes").contentType("application/json")
            .content("{\"contenido\":\"" + texto + "\"}");
        if (quien != null) {
            peticion = peticion.with(quien);
        }
        if (sesion != null) {
            peticion = peticion.header("X-Id-Sesion-Anonima", sesion);
        }
        mvc.perform(peticion).andExpect(status().isOk());
    }

    private UUID conversacion(String clave, boolean autenticado, Instant inicio) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO chatbot.conversaciones (id, identificador_sesion, autenticado, fecha_inicio, fecha_ultima_actividad)
            VALUES (?, ?, ?, ?, ?)
            """, id, clave, autenticado, Timestamp.from(inicio), Timestamp.from(inicio));
        return id;
    }

    // Pregunta y respuesta, la respuesta un segundo despues.
    private UUID intercambio(UUID conversacion, String pregunta, String cuando, Boolean escalado, Integer ms,
                             String resultado, String temaClave) {
        Instant hora = Instant.parse(cuando);
        mensaje(conversacion, "USUARIO", pregunta, hora, null, null, null, null);
        return mensaje(conversacion, "BOT", "respuesta", hora.plusSeconds(1), escalado, ms, resultado, temaClave);
    }

    private UUID mensaje(UUID conversacion, String remitente, String contenido, Instant cuando, Boolean escalado,
                         Integer ms, String resultado, String temaClave) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO chatbot.mensajes (id, conversacion_id, remitente, contenido, fecha_envio, escalado,
              tiempo_respuesta_ms, resultado, tema_clave)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, conversacion, remitente, contenido, Timestamp.from(cuando), escalado, ms, resultado, temaClave);
        return id;
    }

    private void calificacion(UUID mensaje, boolean util, String cuando) {
        jdbc.update("""
            INSERT INTO chatbot.calificaciones (id, mensaje_id, util, fecha_calificacion) VALUES (gen_random_uuid(), ?, ?, ?)
            """, mensaje, util, ts(cuando));
    }

    private void brecha(String normalizado, String ejemplo, int escalamientos, int noUtiles, String primera,
                        String ultima, String estado) {
        jdbc.update("""
            INSERT INTO chatbot.brechas_conocimiento (id, texto_normalizado, ejemplo_pregunta, contador_escalamiento,
              contador_no_util, fecha_primera_ocurrencia, fecha_ultima_ocurrencia, estado)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?)
            """, normalizado, ejemplo, escalamientos, noUtiles, ts(primera), ts(ultima), estado);
    }

    private static Timestamp ts(String instante) {
        return Timestamp.from(Instant.parse(instante));
    }
}
