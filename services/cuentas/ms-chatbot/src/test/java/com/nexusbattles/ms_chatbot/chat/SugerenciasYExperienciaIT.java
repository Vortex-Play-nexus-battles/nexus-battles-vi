package com.nexusbattles.ms_chatbot.chat;

import com.jayway.jsonpath.JsonPath;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ms-chatbot.yaml 1.15.0 contra PostgreSQL real con V1-V20 y la base de
 * conocimiento que despliegan las migraciones:
 *
 * <ul>
 *   <li>7.4.5 «adaptar el nivel de detalle según la experiencia del usuario»
 *       (D-CHA-13): quien nunca eligió recibe la respuesta completa con los
 *       temas relacionados mientras empieza, la normal después y la breve
 *       (con «Más detalle» a un clic) cuando ya conoce el asistente; un nivel
 *       elegido a mano manda, y borrar el historial lo devuelve al principio;</li>
 *   <li>7.4.3 «sugerencias proactivas basadas en el comportamiento del
 *       usuario» (D-CHA-14): lo que se le propone sale de lo que preguntó él,
 *       de dónde está y, si aún no preguntó nada, de lo más consultado; cada
 *       sugerencia tiene respuesta, y nadie recibe las de otro.</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    // La experiencia se mide con decenas de preguntas seguidas.
    "chatbot.limite.mensajes-por-minuto=100",
    "chatbot.limite.mensajes-por-minuto-por-origen=500"
})
@AutoConfigureMockMvc
@DisplayName("Chatbot · nivel de detalle por experiencia y sugerencias para ti (1.15.0)")
class SugerenciasYExperienciaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final String CABECERA = "X-Id-Sesion-Anonima";
    private static final String TORNEO = "Cómo funciona el Torneo";
    private static final String SUBASTA = "Cómo funciona la Subasta";
    private static final String PRIMERA_ORACION_DEL_TORNEO =
        "Los torneos los crea la administración y se abren por temporadas.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // La lista negra es otro servicio: aqui aprueba todo.
    @MockitoBean
    private ModeracionDeContenido moderacion;

    private static RequestPostProcessor tokenDe(UUID uid) {
        return jwt().jwt(j -> j.claim("uid", uid.toString()).claim("rol", "JUGADOR"));
    }

    private static MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder peticion,
                                                      RequestPostProcessor quien, String sesion) {
        MockHttpServletRequestBuilder conQuien = quien == null ? peticion : peticion.with(quien);
        return sesion == null ? conQuien : conQuien.header(CABECERA, sesion);
    }

    private MvcResult escribir(RequestPostProcessor quien, String sesion, String texto) throws Exception {
        return mvc.perform(como(post("/chat/mensajes"), quien, sesion)
                .contentType("application/json")
                .content("{\"contenido\":\"" + texto + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    }

    private String respuesta(RequestPostProcessor quien, String texto) throws Exception {
        return escribir(quien, null, texto).getResponse().getContentAsString();
    }

    private String preferencias(RequestPostProcessor quien) throws Exception {
        return mvc.perform(get("/chat/preferencias").with(quien)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private String elegir(RequestPostProcessor quien, String nivel) throws Exception {
        return mvc.perform(put("/chat/preferencias").with(quien)
                .contentType("application/json")
                .content("{\"idioma\":\"AUTOMATICO\",\"nivelDetalle\":\"" + nivel + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private static String efectivo(String preferencias) {
        return JsonPath.read(preferencias, "$.nivelDetalleEfectivo");
    }

    private List<Map<String, Object>> paraTi(RequestPostProcessor quien, String sesion, String vista, Integer limite)
        throws Exception {
        MockHttpServletRequestBuilder peticion = como(get("/chat/sugerencias/para-ti"), quien, sesion);
        if (vista != null) {
            peticion = peticion.param("vista", vista);
        }
        if (limite != null) {
            peticion = peticion.param("limite", String.valueOf(limite));
        }
        String cuerpo = mvc.perform(peticion).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$");
    }

    private static List<Object> campo(List<Map<String, Object>> sugerencias, String nombre) {
        return sugerencias.stream().map(s -> s.get(nombre)).toList();
    }

    // --- 7.4.5: el nivel de detalle segun la experiencia --------------------

    @Test
    @DisplayName("el nivel automatico sigue lo que ya pregunto; el elegido manda y borrar el historial lo reinicia")
    void elNivelAutomaticoSigueLaExperiencia() throws Exception {
        UUID uid = UUID.randomUUID();
        RequestPostProcessor jugador = tokenDe(uid);

        String alEmpezar = preferencias(jugador);
        assertThat((String) JsonPath.read(alEmpezar, "$.nivelDetalle")).isEqualTo("AUTOMATICO");
        assertThat(efectivo(alEmpezar)).isEqualTo("DETALLADO");

        // Mientras empieza: completa y con los temas relacionados.
        String detallada = JsonPath.read(respuesta(jugador, TORNEO), "$.contenido");
        assertThat(detallada).startsWith(PRIMERA_ORACION_DEL_TORNEO).contains("Temas relacionados:");
        for (int i = 1; i < 5; i++) {
            respuesta(jugador, TORNEO);
        }

        // Cinco resueltas: la respuesta normal.
        assertThat(efectivo(preferencias(jugador))).isEqualTo("NORMAL");
        String normal = JsonPath.read(respuesta(jugador, TORNEO), "$.contenido");
        assertThat(normal).startsWith(PRIMERA_ORACION_DEL_TORNEO).doesNotContain("Temas relacionados:")
            .endsWith("no se transmiten en video.");
        for (int i = 6; i < 30; i++) {
            respuesta(jugador, TORNEO);
        }

        // Treinta: ya conoce el asistente; la primera oracion y «Más detalle».
        assertThat(efectivo(preferencias(jugador))).isEqualTo("BREVE");
        String breve = respuesta(jugador, TORNEO);
        assertThat((String) JsonPath.read(breve, "$.contenido")).isEqualTo(PRIMERA_ORACION_DEL_TORNEO);
        assertThat((String) JsonPath.read(breve, "$.enriquecido.respuestasRapidas[0]")).isEqualTo("Más detalle");

        // El boton da la respuesta entera.
        String entera = JsonPath.read(respuesta(jugador, "Más detalle"), "$.contenido");
        assertThat(entera).startsWith(PRIMERA_ORACION_DEL_TORNEO).contains("no se transmiten en video");

        // Un nivel elegido a mano manda; volver a «automático» recupera el de su experiencia.
        String elegido = elegir(jugador, "NORMAL");
        assertThat((String) JsonPath.read(elegido, "$.nivelDetalle")).isEqualTo("NORMAL");
        assertThat(efectivo(elegido)).isEqualTo("NORMAL");
        assertThat(efectivo(elegir(jugador, "AUTOMATICO"))).isEqualTo("BREVE");
        assertThat(jdbc.queryForObject("SELECT nivel_detalle FROM chatbot.preferencias_chat WHERE clave = ?",
            String.class, uid.toString())).isEqualTo("AUTOMATICO");

        // Sin historial no hay personalizacion (RF-CHA-010): vuelve al principio.
        mvc.perform(delete("/chat/historial").with(jugador)).andExpect(status().isNoContent());
        assertThat(efectivo(preferencias(jugador))).isEqualTo("DETALLADO");

        // La experiencia de uno no cuenta para otro.
        assertThat(efectivo(preferencias(tokenDe(UUID.randomUUID())))).isEqualTo("DETALLADO");
    }

    // --- 7.4.3: sugerencias para ti -------------------------------------------

    @Test
    @DisplayName("salen de lo que pregunto, de donde esta y, sin historial, de lo mas consultado; todas tienen respuesta")
    void lasSugerenciasSalenDeLoQuePregunto() throws Exception {
        UUID uid = UUID.randomUUID();
        RequestPostProcessor jugador = tokenDe(uid);
        Integer conversacionesAntes = jdbc.queryForObject("SELECT count(*) FROM chatbot.conversaciones",
            Integer.class);

        // Sin preguntar nada: para empezar, y en la Subasta, las de la Subasta.
        List<Map<String, Object>> alEmpezar = paraTi(jugador, null, null, null);
        assertThat(alEmpezar).hasSize(3);
        assertThat(campo(alEmpezar, "motivo")).containsOnly("PARA_EMPEZAR");
        assertThat(campo(alEmpezar, "relacionadoCon")).containsOnlyNulls();
        List<Map<String, Object>> enLaSubasta = paraTi(jugador, null, "SUBASTAS", 3);
        assertThat(campo(enLaSubasta, "motivo")).containsOnly("POR_DONDE_ESTAS");
        assertThat(campo(enLaSubasta, "categoria")).containsOnly("SUBASTA_Y_COMERCIO");
        // Pedirlas no abre conversacion ni guarda nada.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chatbot.conversaciones", Integer.class))
            .isEqualTo(conversacionesAntes);

        // Pregunta por la Subasta y abre el chat en Misiones.
        respuesta(jugador, SUBASTA);
        List<Map<String, Object>> despues = paraTi(jugador, null, "MISIONES", 5);

        assertThat(despues).hasSize(5);
        assertThat(campo(despues, "motivo")).containsExactly("POR_LO_QUE_PREGUNTASTE", "POR_LO_QUE_PREGUNTASTE",
            "POR_DONDE_ESTAS", "POR_DONDE_ESTAS", "POR_DONDE_ESTAS");
        assertThat(campo(despues.subList(0, 2), "relacionadoCon")).containsOnly(SUBASTA);
        assertThat(campo(despues.subList(0, 2), "categoria")).containsOnly("SUBASTA_Y_COMERCIO");
        assertThat(campo(despues.subList(2, 5), "categoria")).containsOnly("MODALIDAD_JUEGO");
        assertThat(campo(despues, "pregunta")).doesNotContain(SUBASTA).doesNotHaveDuplicates();

        // Cada una la responde el tema que dice, y ya no se vuelve a proponer.
        for (Map<String, Object> sugerencia : despues) {
            respuesta(jugador, (String) sugerencia.get("pregunta"));
            Map<String, Object> ultima = jdbc.queryForMap("""
                SELECT m.resultado, m.tema_clave FROM chatbot.mensajes m
                JOIN chatbot.conversaciones c ON c.id = m.conversacion_id
                WHERE c.identificador_sesion = ? AND m.remitente = 'BOT'
                ORDER BY m.fecha_envio DESC, m.id DESC LIMIT 1
                """, uid.toString());
            assertThat(ultima).containsEntry("resultado", "TEMA").containsEntry("tema_clave", sugerencia.get("clave"));
        }
        assertThat(campo(paraTi(jugador, null, "MISIONES", 5), "clave"))
            .doesNotContainAnyElementsOf(campo(despues, "clave"));

        // Otra persona no recibe nada de lo que pregunto este jugador.
        List<Map<String, Object>> deOtro = paraTi(tokenDe(UUID.randomUUID()), null, null, 5);
        assertThat(campo(deOtro, "motivo")).containsOnly("PARA_EMPEZAR");
        assertThat(campo(deOtro, "relacionadoCon")).containsOnlyNulls();

        // Sin historial, como quien llega.
        mvc.perform(delete("/chat/historial").with(jugador)).andExpect(status().isNoContent());
        assertThat(campo(paraTi(jugador, null, null, null), "motivo")).containsOnly("PARA_EMPEZAR");
    }

    @Test
    @DisplayName("un visitante recibe las suyas con su sesion; un uid ajeno en la cabecera no abre las de nadie")
    void unVisitanteRecibeLasSuyas() throws Exception {
        UUID uid = UUID.randomUUID();
        respuesta(tokenDe(uid), SUBASTA);

        MvcResult primera = escribir(null, null, TORNEO);
        String sesion = primera.getResponse().getHeader(CABECERA);
        assertThat(sesion).startsWith("anon_");

        List<Map<String, Object>> delVisitante = paraTi(null, sesion, null, 2);
        assertThat(campo(delVisitante, "motivo")).containsOnly("POR_LO_QUE_PREGUNTASTE");
        assertThat(campo(delVisitante, "relacionadoCon")).containsOnly(TORNEO);
        assertThat(campo(delVisitante, "categoria")).containsOnly("MODALIDAD_JUEGO");

        // B11: el uid de un jugador como sesion de visitante no abre su conversacion.
        Integer sesionesAntes = jdbc.queryForObject("SELECT count(*) FROM chatbot.sesiones_anonimas", Integer.class);
        List<Map<String, Object>> conUidAjeno = paraTi(null, uid.toString(), null, 5);
        assertThat(campo(conUidAjeno, "motivo")).containsOnly("PARA_EMPEZAR");
        assertThat(campo(conUidAjeno, "relacionadoCon")).doesNotContain(SUBASTA);
        // Y pedir sugerencias nunca registra una sesion.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chatbot.sesiones_anonimas", Integer.class))
            .isEqualTo(sesionesAntes);
    }
}
