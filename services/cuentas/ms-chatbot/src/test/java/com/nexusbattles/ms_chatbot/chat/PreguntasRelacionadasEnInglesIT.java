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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ms-chatbot.yaml 1.17.0 contra PostgreSQL real con V1-V21 y la base que
 * despliegan las migraciones (7.4.2 «comprensión de consultas en lenguaje
 * natural en español/english» y «sugerencias de preguntas relacionadas
 * basadas en el contexto», HU-CHA-004):
 *
 * <ul>
 *   <li>a una pregunta en inglés, las preguntas relacionadas (los botones y
 *       los «Related topics» del texto) salen con su título en inglés, y
 *       pulsar cada una responde ese tema, también en inglés;</li>
 *   <li>en español, igual que siempre: títulos y respuestas en español;</li>
 *   <li>quien eligió el español recibe el español aunque pulse un título en
 *       inglés: la preferencia manda;</li>
 *   <li>el autocompletado sugiere el título en inglés a quien escribe en
 *       inglés, y el de siempre a quien escribe en español.</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "chatbot.limite.mensajes-por-minuto=100",
    "chatbot.limite.mensajes-por-minuto-por-origen=500"
})
@AutoConfigureMockMvc
@DisplayName("Chatbot · las preguntas relacionadas en el idioma de la conversación (1.17.0)")
class PreguntasRelacionadasEnInglesIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final String CABECERA = "X-Id-Sesion-Anonima";
    private static final String DE_PRODUCCION = " FROM chatbot.temas_conocimiento t "
        + "JOIN chatbot.versiones_base_conocimiento v ON v.id = t.version_id WHERE v.estado = 'PRODUCCION'";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // La lista negra es otro servicio: aqui aprueba todo.
    @MockitoBean
    private ModeracionDeContenido moderacion;

    private MvcResult escribir(RequestPostProcessor quien, String sesion, String texto) throws Exception {
        MockHttpServletRequestBuilder peticion = post("/chat/mensajes").contentType("application/json")
            .content("{\"contenido\":\"" + texto + "\"}");
        if (quien != null) {
            peticion = peticion.with(quien);
        }
        if (sesion != null) {
            peticion = peticion.header(CABECERA, sesion);
        }
        return mvc.perform(peticion).andExpect(status().isOk()).andReturn();
    }

    private static String cuerpo(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString();
    }

    private static List<String> relacionadas(String respuesta) {
        return JsonPath.read(respuesta, "$.enriquecido.respuestasRapidas");
    }

    private static String contenido(String respuesta) {
        return JsonPath.read(respuesta, "$.contenido");
    }

    private List<String> titulos(String columna) {
        return jdbc.queryForList("SELECT t." + columna + DE_PRODUCCION + " AND t." + columna + " IS NOT NULL",
            String.class);
    }

    /** El texto de un tema de produccion, buscado por su titulo en un idioma. */
    private String textoDe(String columnaDelTitulo, String titulo, String columnaDelTexto) {
        return jdbc.queryForObject("SELECT t." + columnaDelTexto + DE_PRODUCCION + " AND t." + columnaDelTitulo
            + " = ?", String.class, titulo);
    }

    private static String primeraOracion(String texto) {
        int fin = texto.indexOf(". ");
        return fin < 0 ? texto.strip() : texto.substring(0, fin + 1);
    }

    @Test
    @DisplayName("en ingles las relacionadas salen en ingles y cada una responde su tema en ingles")
    void enInglesLasRelacionadasSalenEnInglesYRespondenEnIngles() throws Exception {
        MvcResult primera = escribir(null, null, "How does the tournament work?");
        String sesion = primera.getResponse().getHeader(CABECERA);
        String respuesta = cuerpo(primera);

        assertThat(contenido(respuesta))
            .startsWith(primeraOracion(textoDe("titulo_en", "How the Tournament works", "contenido_respuesta_en")));
        List<String> enIngles = relacionadas(respuesta);
        assertThat(enIngles).isNotEmpty().allSatisfy(titulo -> assertThat(titulos("titulo_en")).contains(titulo));
        assertThat(enIngles).noneMatch(titulos("titulo")::contains);
        // Quien empieza recibe tambien los «Related topics» en el texto: los mismos, en ingles.
        assertThat(contenido(respuesta)).endsWith("Related topics: " + String.join(" | ", enIngles) + ".");

        // Pulsar cada una responde ese tema, en ingles, con sus relacionadas tambien en ingles.
        for (String titulo : enIngles) {
            String pulsada = cuerpo(escribir(null, sesion, titulo));
            assertThat(contenido(pulsada)).as(titulo)
                .startsWith(primeraOracion(textoDe("titulo_en", titulo, "contenido_respuesta_en")));
            assertThat(relacionadas(pulsada)).as(titulo).noneMatch(titulos("titulo")::contains);
        }
    }

    @Test
    @DisplayName("en espanol, las de siempre: titulos y respuestas en espanol")
    void enEspanolLasDeSiempre() throws Exception {
        MvcResult primera = escribir(null, null, "¿Cómo funciona el torneo?");
        String sesion = primera.getResponse().getHeader(CABECERA);
        String respuesta = cuerpo(primera);

        List<String> enEspanol = relacionadas(respuesta);
        assertThat(enEspanol).isNotEmpty().allSatisfy(titulo -> assertThat(titulos("titulo")).contains(titulo));
        assertThat(contenido(respuesta)).endsWith("Temas relacionados: " + String.join(" | ", enEspanol) + ".");
        for (String titulo : enEspanol) {
            assertThat(contenido(cuerpo(escribir(null, sesion, titulo)))).as(titulo)
                .startsWith(primeraOracion(textoDe("titulo", titulo, "contenido_respuesta_es")));
        }
    }

    @Test
    @DisplayName("con el espanol elegido, un titulo en ingles responde en espanol: la preferencia manda")
    void conElEspanolElegidoLaPreferenciaManda() throws Exception {
        RequestPostProcessor jugadora = jwt().jwt(j -> j.claim("uid", UUID.randomUUID().toString())
            .claim("rol", "JUGADOR"));
        mvc.perform(put("/chat/preferencias").with(jugadora)
                .contentType("application/json")
                .content("{\"idioma\":\"ES\",\"nivelDetalle\":\"NORMAL\"}"))
            .andExpect(status().isOk());

        String respuesta = cuerpo(escribir(jugadora, null, "How the Auction works"));

        String textoEs = textoDe("titulo_en", "How the Auction works", "contenido_respuesta_es");
        assertThat(contenido(respuesta)).startsWith(primeraOracion(textoEs)).doesNotContain("Related topics");
        assertThat(relacionadas(respuesta)).isNotEmpty()
            .allSatisfy(titulo -> assertThat(titulos("titulo")).contains(titulo));
        Map<String, Object> elTema = jdbc.queryForMap("SELECT t.clave, t.titulo" + DE_PRODUCCION
            + " AND t.titulo_en = 'How the Auction works'");
        assertThat(elTema).containsEntry("clave", "como-funciona-la-subasta")
            .containsEntry("titulo", "Cómo funciona la Subasta");
    }

    @Test
    @DisplayName("el autocompletado sugiere en el idioma de lo escrito")
    void elAutocompletadoSigueElIdiomaDeLoEscrito() throws Exception {
        String enIngles = mvc.perform(get("/chat/sugerencias").param("q", "how the tourn"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(enIngles, "$[0].titulo")).isEqualTo("How the Tournament works");
        assertThat((String) JsonPath.read(enIngles, "$[0].pregunta")).isEqualTo("How the Tournament works");

        String enEspanol = mvc.perform(get("/chat/sugerencias").param("q", "como funciona el tor"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(enEspanol, "$[0].pregunta")).isEqualTo("Cómo funciona el Torneo");
    }
}
