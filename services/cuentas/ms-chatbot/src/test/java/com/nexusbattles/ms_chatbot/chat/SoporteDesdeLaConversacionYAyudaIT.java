package com.nexusbattles.ms_chatbot.chat;

import com.jayway.jsonpath.JsonPath;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ms-chatbot.yaml 1.8.0 (7.4.3) contra PostgreSQL real con V1-V13: la
 * solicitud desde la conversacion (redaccion, conversacion propia, 409, 401
 * del visitante) y el centro de ayuda (solo produccion, solo activos, nada
 * interno, en cache).
 */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@DisplayName("Chatbot · solicitud desde la conversacion y centro de ayuda (1.8.0)")
class SoporteDesdeLaConversacionYAyudaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final String DESDE_CONVERSACION = "/chat/tickets/desde-conversacion";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // La lista negra es otro servicio: aqui aprueba todo.
    @MockitoBean
    private ModeracionDeContenido moderacion;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM chatbot.tickets_soporte");
        jdbc.update("DELETE FROM chatbot.conversaciones");
        jdbc.update("DELETE FROM chatbot.brechas_conocimiento");
    }

    private static RequestPostProcessor jugador(UUID uid) {
        return jwt().jwt(j -> j.claim("uid", uid.toString()));
    }

    // ------------------------------------------------- solicitud en un clic

    @Test
    @DisplayName("cada jugador abre la suya con SU pregunta sin resolver, redactada, y una sola a la vez")
    void cadaJugadorConSuPreguntaRedactadaYUnaSolaALaVez() throws Exception {
        UUID ana = UUID.randomUUID();
        UUID beto = UUID.randomUUID();
        escribir(jugador(ana), "zxqwv plorf 4111 1111 1111 1111");
        escribir(jugador(beto), "qwzx vbnm grrr");

        String deAna = mvc.perform(post(DESDE_CONVERSACION).with(jugador(ana))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vista\":\"SUBASTAS\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.estado").value("ABIERTO"))
            .andExpect(jsonPath("$.categoria").value("SUBASTA_Y_COMERCIO"))
            .andExpect(jsonPath("$.asunto").value("Sin respuesta del asistente: zxqwv plorf [tarjeta]"))
            .andExpect(jsonPath("$.mensaje")
                .value("Le pregunté al asistente: «zxqwv plorf [tarjeta]» y no la entendió."))
            .andExpect(jsonPath("$.uid").doesNotExist())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        mvc.perform(post(DESDE_CONVERSACION).with(jugador(beto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.categoria").value("FAQ_GENERAL"))
            .andExpect(jsonPath("$.asunto").value("Sin respuesta del asistente: qwzx vbnm grrr"));

        // Nada de la tarjeta en la base, ni en el ticket ni en su contexto.
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM chatbot.tickets_soporte t
            WHERE t.asunto LIKE '%4111%' OR t.mensaje LIKE '%4111%'
               OR EXISTS (SELECT 1 FROM chatbot.tickets_soporte_contexto c
                          WHERE c.ticket_id = t.id AND c.contenido LIKE '%4111%')
            """, Integer.class)).isZero();
        // El de Ana lleva su conversacion, no la de Beto.
        List<String> contextoDeAna = jdbc.queryForList("""
            SELECT c.contenido FROM chatbot.tickets_soporte_contexto c
            JOIN chatbot.tickets_soporte t ON t.id = c.ticket_id WHERE t.uid = ?
            """, String.class, ana.toString());
        assertThat(contextoDeAna).anyMatch(texto -> texto.contains("zxqwv plorf"))
            .noneMatch(texto -> texto.contains("qwzx"));

        // Una abierta a la vez, y se ve en «Tus solicitudes» de quien la abrio.
        mvc.perform(post(DESDE_CONVERSACION).with(jugador(ana)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.motivo").value("TICKET_ABIERTO"));
        String id = JsonPath.read(deAna, "$.ticketId");
        mvc.perform(get("/chat/tickets").with(jugador(ana)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].ticketId").value(id));
    }

    @Test
    @DisplayName("tras un «no me sirvio» lleva la pregunta que respondio el tema, con la categoria del tema")
    void trasUnNoMeSirvioLaPreguntaAnteriorYSuCategoria() throws Exception {
        UUID uid = UUID.randomUUID();
        UUID conversacion = conversacion(uid.toString());
        Instant inicio = Instant.parse("2026-10-14T15:00:00Z");
        mensaje(conversacion, "USUARIO", "como recupero mi cuenta", inicio, null, null);
        mensaje(conversacion, "BOT", "Para recuperarla...", inicio.plusSeconds(1), "TEMA", "CUENTA_Y_REGISTRO");
        mensaje(conversacion, "USUARIO", "no me sirvió", inicio.plusSeconds(2), null, null);
        mensaje(conversacion, "BOT", "Siento que no sirviera.", inicio.plusSeconds(3), "NO_SIRVIO", "CUENTA_Y_REGISTRO");

        mvc.perform(post(DESDE_CONVERSACION).with(jugador(uid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vista\":\"SUBASTAS\",\"comentario\":\"Ya lo intente dos veces.\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.categoria").value("CUENTA_Y_REGISTRO"))
            .andExpect(jsonPath("$.asunto").value("Sin respuesta del asistente: como recupero mi cuenta"))
            .andExpect(jsonPath("$.mensaje").value("Le pregunté al asistente: «como recupero mi cuenta» y su "
                + "respuesta no me sirvió.\n\nYa lo intente dos veces."));
    }

    @Test
    @DisplayName("un visitante recibe 401 y un jugador sin preguntas 409 SIN_CONVERSACION; nada se guarda")
    void visitante401YSinConversacion409() throws Exception {
        String sesion = "visitante-" + UUID.randomUUID();
        escribirComoVisitante(sesion, "zxqwv plorf");

        mvc.perform(post(DESDE_CONVERSACION).header("X-Id-Sesion-Anonima", sesion))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.motivo").value("SESION_REQUERIDA"));
        mvc.perform(post(DESDE_CONVERSACION).with(jugador(UUID.randomUUID())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.motivo").value("SIN_CONVERSACION"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chatbot.tickets_soporte", Integer.class)).isZero();
    }

    // ------------------------------------------------------- centro de ayuda

    @Test
    @DisplayName("el centro de ayuda trae solo los temas activos en produccion, sin nada interno y en cache")
    void elCentroDeAyudaSoloProduccionActivosYSinNadaInterno() throws Exception {
        // Una candidata con un tema que todavia no esta publicado...
        RequestPostProcessor admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
        mvc.perform(post("/chatbot/admin/base-conocimiento/borrador").with(admin)).andExpect(status().isCreated());
        mvc.perform(post("/chatbot/admin/base-conocimiento/borrador/temas").with(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"categoria": "FAQ_GENERAL", "tipoRespuesta": "DIRECTA", "titulo": "Tema solo de la candidata",
                     "variantesEs": ["tema solo de la candidata"], "respuestaEs": "Aun no.", "prioridad": 0}
                    """))
            .andExpect(status().isCreated());
        // ...y uno de produccion desactivado.
        jdbc.update("""
            UPDATE chatbot.temas_conocimiento SET activo = false WHERE clave = 'cancelar-subasta'
              AND version_id = (SELECT id FROM chatbot.versiones_base_conocimiento WHERE estado = 'PRODUCCION')
            """);

        String json = mvc.perform(get("/chat/ayuda"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=300, public"))
            .andExpect(jsonPath("$.categorias[0].categoria").value("PRODUCTO"))
            .andExpect(jsonPath("$.categorias[0].ancla").value("categoria-producto"))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<String> titulos = JsonPath.read(json, "$.categorias[*].temas[*].titulo");
        // 62 publicados (V11, V15 y V18), sin saludo ni despedida, sin el desactivado.
        assertThat(titulos).hasSize(59)
            .doesNotContain("Tema solo de la candidata", "Saludo", "Despedida");
        assertThat(jdbc.queryForObject("""
            SELECT titulo FROM chatbot.temas_conocimiento WHERE clave = 'cancelar-subasta'
              AND version_id = (SELECT id FROM chatbot.versiones_base_conocimiento WHERE estado = 'PRODUCCION')
            """, String.class)).isNotIn(titulos);
        List<String> categorias = JsonPath.read(json, "$.categorias[*].categoria");
        assertThat(categorias).containsExactly("PRODUCTO", "MECANICA_JUEGO", "MODALIDAD_JUEGO", "CUENTA_Y_REGISTRO",
            "SUBASTA_Y_COMERCIO", "SOPORTE_TECNICO", "POLITICAS_Y_TERMINOS", "FAQ_GENERAL");
        List<List<String>> pasos = JsonPath.read(json, "$.categorias[*].temas[*].pasosEs");
        assertThat(pasos).anyMatch(lista -> lista.size() >= 2);
        // Nada interno de un tema ni de la version.
        List<Map<String, Object>> grupos = JsonPath.read(json, "$.categorias[*]");
        assertThat(grupos).allSatisfy(grupo -> assertThat(grupo).containsOnlyKeys("categoria", "ancla", "temas"));
        List<Map<String, Object>> temas = JsonPath.read(json, "$.categorias[*].temas[*]");
        assertThat(temas).allSatisfy(tema -> assertThat(tema).containsOnlyKeys("titulo", "respuestaEs",
            "respuestaEn", "pasosEs", "pasosEn"));
        assertThat(json).doesNotContain("\"clave\":", "\"prioridad\":", "\"variantesEs\":", "\"palabrasClaveEs\":",
            "\"version\":", "\"id\":", "\"activo\":");
    }

    // ------------------------------------------------------------- auxiliares

    private void escribir(RequestPostProcessor quien, String texto) throws Exception {
        mvc.perform(post("/chat/mensajes").with(quien)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contenido\":\"" + texto + "\"}"))
            .andExpect(status().isOk());
    }

    private void escribirComoVisitante(String sesion, String texto) throws Exception {
        MockHttpServletRequestBuilder peticion = post("/chat/mensajes").header("X-Id-Sesion-Anonima", sesion)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"contenido\":\"" + texto + "\"}");
        mvc.perform(peticion).andExpect(status().isOk());
    }

    private UUID conversacion(String clave) {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.parse("2026-10-14T15:00:00Z"));
        jdbc.update("""
            INSERT INTO chatbot.conversaciones (id, identificador_sesion, autenticado, fecha_inicio, fecha_ultima_actividad)
            VALUES (?, ?, true, ?, ?)
            """, id, clave, ahora, ahora);
        return id;
    }

    private void mensaje(UUID conversacion, String remitente, String contenido, Instant cuando, String resultado,
                         String categoria) {
        jdbc.update("""
            INSERT INTO chatbot.mensajes (id, conversacion_id, remitente, contenido, fecha_envio, escalado, resultado,
              categoria)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?)
            """, conversacion, remitente, contenido, Timestamp.from(cuando),
            "BOT".equals(remitente) ? "NO_SIRVIO".equals(resultado) : null, resultado, categoria);
    }
}
