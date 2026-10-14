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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1.4.0 — 7.4.8 de punta a punta, con la aplicación entera y PostgreSQL real:
 * ningún secreto llega a la base (mensajes, brechas, comentarios), un intento
 * de inyección se rechaza sin dejar rastro, y un token sin {@code uid} nunca
 * se toma por el jugador cuyo uid coincide con su {@code sub}.
 */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@DisplayName("Chatbot · privacidad e inyeccion de punta a punta (7.4.8)")
class PrivacidadDelChatIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final String CONTRASENA = "Hunter2!x";
    private static final String TARJETA = "4111 1111 1111 1111";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ModeracionDeContenido moderacion;

    private static RequestPostProcessor tokenDe(UUID uid) {
        return jwt().jwt(j -> j.claim("uid", uid.toString()).claim("rol", "JUGADOR"));
    }

    private String enviar(RequestPostProcessor quien, String cuerpoJson, int estado) throws Exception {
        return mvc.perform(post("/chat/mensajes").with(quien).contentType("application/json").content(cuerpoJson))
            .andExpect(status().is(estado))
            .andReturn().getResponse().getContentAsString();
    }

    /** Todo el texto libre que guarda el chatbot, en una sola lista. */
    private List<String> todoLoGuardado() {
        return jdbc.queryForList(
            "SELECT contenido FROM chatbot.mensajes UNION ALL SELECT ejemplo_pregunta FROM chatbot.brechas_conocimiento "
                + "UNION ALL SELECT texto_normalizado FROM chatbot.brechas_conocimiento "
                + "UNION ALL SELECT comentario FROM chatbot.calificaciones WHERE comentario IS NOT NULL", String.class);
    }

    @Test
    @DisplayName("la contrasena y la tarjeta no llegan a mensajes, brechas ni comentarios")
    void ningunSecretoSeGuarda() throws Exception {
        UUID jugador = UUID.randomUUID();
        String respuesta = enviar(tokenDe(jugador),
            "{\"contenido\":\"zxqw no puedo entrar, mi contraseña es " + CONTRASENA + " y mi tarjeta " + TARJETA + "\"}",
            200);
        String idRespuesta = JsonPath.read(respuesta, "$.id");
        assertThat((String) JsonPath.read(respuesta, "$.contenido")).startsWith("Por tu seguridad tapé");

        mvc.perform(post("/chat/mensajes/{id}/calificacion", idRespuesta).with(tokenDe(jugador))
                .contentType("application/json")
                .content("{\"util\":false,\"comentario\":\"no sirvio; password: " + CONTRASENA + "\"}"))
            .andExpect(status().isCreated());

        String historial = mvc.perform(get("/chat/historial").with(tokenDe(jugador)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(historial).doesNotContain(CONTRASENA).doesNotContain(TARJETA);

        List<String> guardado = todoLoGuardado();
        assertThat(guardado).isNotEmpty();
        // Las brechas guardan ademas el texto normalizado (minusculas, sin signos).
        assertThat(String.join("\n", guardado).toLowerCase())
            .doesNotContain("hunter2")
            .doesNotContain(TARJETA)
            .doesNotContain(TARJETA.replace(" ", ""))
            .doesNotContain("4111 1111");
    }

    @Test
    @DisplayName("un intento de inyeccion responde 422 INYECCION_DETECTADA y no guarda nada")
    void laInyeccionNoDejaRastro() throws Exception {
        UUID jugador = UUID.randomUUID();
        int antes = jdbc.queryForObject("SELECT count(*) FROM chatbot.mensajes", Integer.class);

        mvc.perform(post("/chat/mensajes").with(tokenDe(jugador)).contentType("application/json")
                .content("{\"contenido\":\"1' UNION SELECT clave FROM cuentas --\"}"))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.motivo").value("INYECCION_DETECTADA"))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("UNION"))));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM chatbot.mensajes", Integer.class)).isEqualTo(antes);
    }

    @Test
    @DisplayName("un NUL ya no tumba la peticion con un 500: se quita antes de guardar")
    void unNulNoRompeLaBase() throws Exception {
        UUID jugador = UUID.randomUUID();
        enviar(tokenDe(jugador), "{\"contenido\":\"ho\\u0000la\"}", 200);

        String historial = mvc.perform(get("/chat/historial").with(tokenDe(jugador)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(historial, "$[0].contenido")).isEqualTo("hola");
    }

    @Test
    @DisplayName("un token sin uid con el uid de otro jugador en sub no abre su conversacion")
    void elSubNuncaEsElUid() throws Exception {
        UUID victima = UUID.randomUUID();
        enviar(tokenDe(victima), "{\"contenido\":\"hola\"}", 200);

        mvc.perform(get("/chat/historial").with(jwt().jwt(j -> j.subject(victima.toString()).claim("rol", "JUGADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }
}
