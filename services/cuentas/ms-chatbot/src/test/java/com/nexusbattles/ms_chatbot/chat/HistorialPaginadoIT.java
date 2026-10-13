package com.nexusbattles.ms_chatbot.chat;

import com.jayway.jsonpath.JsonPath;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ms-chatbot.yaml 1.3.5 — el historial por paginas contra PostgreSQL real:
 * la consulta con cursor (fecha y, en empate, id) recorre la conversacion
 * hacia atras sin repetir ni saltarse mensajes, y un cursor ajeno no abre
 * nada.
 */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@DisplayName("Chatbot · historial por paginas (1.3.5)")
class HistorialPaginadoIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mvc;

    // La lista negra es otro servicio: aqui aprueba todo.
    @MockitoBean
    private ModeracionDeContenido moderacion;

    private static RequestPostProcessor tokenDe(UUID uid) {
        return jwt().jwt(j -> j.claim("uid", uid.toString()).claim("rol", "JUGADOR"));
    }

    private void escribir(RequestPostProcessor quien, String texto) throws Exception {
        mvc.perform(post("/chat/mensajes").with(quien)
                .contentType("application/json")
                .content("{\"contenido\":\"" + texto + "\"}"))
            .andExpect(status().isOk());
    }

    private List<Map<String, Object>> pagina(RequestPostProcessor quien, String antesDe, int limite) throws Exception {
        MockHttpServletRequestBuilder peticion = get("/chat/historial").with(quien)
            .param("limite", String.valueOf(limite));
        if (antesDe != null) {
            peticion = peticion.param("antesDe", antesDe);
        }
        String cuerpo = mvc.perform(peticion).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$");
    }

    @Test
    @DisplayName("recorre la conversacion hacia atras sin repetir ni saltarse mensajes")
    void recorreHaciaAtras() throws Exception {
        RequestPostProcessor jugador = tokenDe(UUID.randomUUID());
        for (String texto : List.of("uno", "dos", "tres")) {
            escribir(jugador, texto);
        }

        // 6 mensajes: uno, bot, dos, bot, tres, bot. La ultima pagina de 4
        // empieza en "dos" y va en orden cronologico.
        List<Map<String, Object>> ultima = pagina(jugador, null, 4);
        assertThat(ultima).hasSize(4);
        assertThat(ultima.get(0).get("contenido")).isEqualTo("dos");
        assertThat(ultima.get(2).get("contenido")).isEqualTo("tres");

        List<Map<String, Object>> anterior = pagina(jugador, (String) ultima.get(0).get("id"), 4);
        assertThat(anterior).hasSize(2);
        assertThat(anterior.get(0).get("contenido")).isEqualTo("uno");

        assertThat(pagina(jugador, (String) anterior.get(0).get("id"), 4)).as("no hay mas hacia atras").isEmpty();

        // Sin parametros, todo el historial, igual que antes de 1.3.5.
        String todo = mvc.perform(get("/chat/historial").with(jugador)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        Integer cuantos = JsonPath.read(todo, "$.length()");
        assertThat(cuantos).isEqualTo(6);
    }

    @Test
    @DisplayName("un cursor de otra conversacion no devuelve nada")
    void cursorAjeno() throws Exception {
        RequestPostProcessor victima = tokenDe(UUID.randomUUID());
        escribir(victima, "secreto");
        String idAjeno = (String) pagina(victima, null, 1).get(0).get("id");

        RequestPostProcessor otro = tokenDe(UUID.randomUUID());
        escribir(otro, "hola");

        assertThat(pagina(otro, idAjeno, 10)).isEmpty();
    }
}
