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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ms-chatbot.yaml 1.12.0 (7.4.3, RF-CHA-007 «generar automáticamente un
 * ticket de soporte», actores Visitante y Jugador; segunda auditoría del 7.4)
 * contra PostgreSQL real con V1-V18: la pregunta que el asistente no resuelve
 * abre sola la solicitud, la siguiente se suma a ella, un visitante también
 * tiene las suyas y nadie ve las de otro.
 */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@DisplayName("Chatbot · la solicitud de soporte se abre sola (1.12.0)")
class SolicitudesAutomaticasIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final String CABECERA = "X-Id-Sesion-Anonima";

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

    private static RequestPostProcessor administrador() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
    }

    // ------------------------------------------------------------- jugador

    @Test
    @DisplayName("jugador: la primera sin resolver abre la solicitud, la siguiente se suma y una resuelta no la toca")
    void laPrimeraAbreLaSiguienteSeSumaYUnaResueltaNoLaToca() throws Exception {
        UUID ana = UUID.randomUUID();

        String primera = escribir(jugador(ana), "zxqwv plorf 4111 1111 1111 1111", "SUBASTAS");
        assertThat((String) JsonPath.read(primera, "$.contenido"))
            .contains("Por tu seguridad tapé un dato sensible")
            .contains("No estoy seguro de haber entendido tu consulta.")
            .endsWith("Abrí una solicitud de soporte con esta conversación: una persona del equipo te responderá "
                + "aquí, en «Soporte» › «Tus solicitudes».");
        assertThat((Boolean) JsonPath.read(primera, "$.enriquecido.solicitud.nueva")).isTrue();
        assertThat((String) JsonPath.read(primera, "$.enriquecido.solicitud.estado")).isEqualTo("ABIERTO");
        assertThat((String) JsonPath.read(primera, "$.enriquecido.solicitud.asunto"))
            .isEqualTo("Sin respuesta del asistente: zxqwv plorf [tarjeta]");
        String id = JsonPath.read(primera, "$.enriquecido.solicitud.id");

        String segunda = escribir(jugador(ana), "qwrtp lkjhg mnbvc", null);
        assertThat((String) JsonPath.read(segunda, "$.contenido"))
            .endsWith("Sumé esta pregunta a tu solicitud abierta en «Soporte»: soporte la verá con el resto de la "
                + "conversación.");
        assertThat((Boolean) JsonPath.read(segunda, "$.enriquecido.solicitud.nueva")).isFalse();
        assertThat((String) JsonPath.read(segunda, "$.enriquecido.solicitud.id")).isEqualTo(id);

        // Una sola, de la vista en la que estaba, redactada, con las dos preguntas.
        assertThat(jdbc.queryForList("SELECT categoria FROM chatbot.tickets_soporte WHERE uid = ?", String.class,
            ana.toString())).containsExactly("SUBASTA_Y_COMERCIO");
        List<String> contexto = contextoDe(id);
        assertThat(contexto).anyMatch(texto -> texto.contains("zxqwv plorf [tarjeta]"))
            .anyMatch(texto -> texto.contains("qwrtp lkjhg mnbvc"))
            .noneMatch(texto -> texto.contains("4111"));

        // Una pregunta que la base responde no abre ni suma nada.
        String resuelta = escribir(jugador(ana), "como funciona la subasta", null);
        assertThat(resuelta).doesNotContain("\"solicitud\":{").doesNotContain("Abrí una solicitud");
        assertThat(contextoDe(id)).hasSameSizeAs(contexto);

        // El historial guarda la respuesta como se envio, con su solicitud.
        mvc.perform(get("/chat/historial").with(jugador(ana)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[1].enriquecido.solicitud.id").value(id))
            .andExpect(jsonPath("$[1].contenido").value(containsString("Abrí una solicitud de soporte")));

        // «Tus solicitudes»: la suya, sin uid ni contexto.
        mvc.perform(get("/chat/tickets").with(jugador(ana)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].ticketId").value(id))
            .andExpect(jsonPath("$[0].uid").doesNotExist())
            .andExpect(jsonPath("$[0].contexto").doesNotExist());
        // El boton de la ventana ya no abre otra: hay una abierta.
        mvc.perform(post("/chat/tickets/desde-conversacion").with(jugador(ana)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.motivo").value("TICKET_ABIERTO"));
    }

    @Test
    @DisplayName("jugador: resuelta la solicitud, la siguiente pregunta sin resolver abre otra")
    void resueltaLaSolicitudLaSiguienteAbreOtra() throws Exception {
        UUID beto = UUID.randomUUID();
        String primera = escribir(jugador(beto), "zxqwv plorf", null);
        String id = JsonPath.read(primera, "$.enriquecido.solicitud.id");

        mvc.perform(patch("/chatbot/admin/tickets/" + id).with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"RESUELTO\",\"respuesta\":\"Ya quedó.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deVisitante").value(false));

        String otra = escribir(jugador(beto), "qwrtp lkjhg", null);
        assertThat((Boolean) JsonPath.read(otra, "$.enriquecido.solicitud.nueva")).isTrue();
        assertThat((String) JsonPath.read(otra, "$.enriquecido.solicitud.id")).isNotEqualTo(id);
        mvc.perform(get("/chat/tickets").with(jugador(beto)))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[1].estado").value("RESUELTO"))
            .andExpect(jsonPath("$[1].respuesta").value("Ya quedó."));
    }

    @Test
    @DisplayName("jugador: «no me sirvió» abre la solicitud con la pregunta que respondió el tema y su categoría")
    void noMeSirvioAbreLaSolicitudConLaPreguntaDelTema() throws Exception {
        UUID carla = UUID.randomUUID();
        escribir(jugador(carla), "como funciona la subasta", null);
        String categoriaDelTema = jdbc.queryForObject("""
            SELECT m.categoria FROM chatbot.mensajes m JOIN chatbot.conversaciones c ON c.id = m.conversacion_id
            WHERE c.identificador_sesion = ? AND m.remitente = 'BOT'
            """, String.class, carla.toString());
        assertThat(categoriaDelTema).isNotNull();

        String noSirvio = escribir(jugador(carla), "no me sirvió", null);
        assertThat((String) JsonPath.read(noSirvio, "$.contenido"))
            .startsWith("Siento que mi respuesta no te haya servido.")
            .contains("Abrí una solicitud de soporte");
        String id = JsonPath.read(noSirvio, "$.enriquecido.solicitud.id");

        mvc.perform(get("/chat/tickets").with(jugador(carla)))
            .andExpect(jsonPath("$[0].ticketId").value(id))
            .andExpect(jsonPath("$[0].categoria").value(categoriaDelTema))
            .andExpect(jsonPath("$[0].asunto").value("Sin respuesta del asistente: como funciona la subasta"))
            .andExpect(jsonPath("$[0].mensaje")
                .value("Le pregunté al asistente: «como funciona la subasta» y su respuesta no me sirvió."));
        // «no me sirvió» no es una pregunta sin cubrir: no va a las brechas.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chatbot.brechas_conocimiento", Integer.class)).isZero();
    }

    // ------------------------------------------------------------ visitante

    @Test
    @DisplayName("visitante: su pregunta sin resolver abre SU solicitud, que solo ve su sesión, y el admin la reconoce")
    void elVisitanteTieneLaSuyaYSoloLaVeSuSesion() throws Exception {
        String sesion = "visitante-" + UUID.randomUUID();
        String otra = "visitante-" + UUID.randomUUID();

        String respuesta = escribir(visitante(sesion), "zxqwv plorf", null);
        assertThat((String) JsonPath.read(respuesta, "$.contenido"))
            .endsWith("mientras sigas en esta sesión de visitante.");
        String id = JsonPath.read(respuesta, "$.enriquecido.solicitud.id");

        mvc.perform(get("/chat/tickets").header(CABECERA, sesion))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].ticketId").value(id))
            .andExpect(jsonPath("$[0].uid").doesNotExist());
        // Otra sesion (aun sin registrar) no tiene ninguna; un jugador tampoco ve la del visitante.
        mvc.perform(get("/chat/tickets").header(CABECERA, otra))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/chat/tickets").with(jugador(UUID.randomUUID())).header(CABECERA, sesion))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        // Sin token ni sesion, como antes: 401.
        mvc.perform(get("/chat/tickets"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.motivo").value("SESION_REQUERIDA"));

        // Es de la clave de su conversacion, nunca de un uid; el admin lo sabe.
        String clave = jdbc.queryForObject("SELECT uid FROM chatbot.tickets_soporte WHERE id = ?::uuid",
            String.class, id);
        assertThat(clave).startsWith("anonimo:").isNotEqualTo("anonimo:" + sesion);
        mvc.perform(get("/chatbot/admin/tickets/" + id).with(administrador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deVisitante").value(true))
            .andExpect(jsonPath("$.uid").value(clave))
            .andExpect(jsonPath("$.contexto[0].contenido").value("zxqwv plorf"));
    }

    @Test
    @DisplayName("visitante: abre una con el formulario aunque aún no haya escrito, y una sola a la vez")
    void elVisitanteAbreUnaConElFormularioYUnaSolaALaVez() throws Exception {
        String sesion = "visitante-" + UUID.randomUUID();
        String cuerpo = "{\"categoria\":\"CUENTA_Y_REGISTRO\",\"asunto\":\"No me llega el correo\","
            + "\"mensaje\":\"Me registre y no me llega el codigo.\"}";

        mvc.perform(post("/chat/tickets").header(CABECERA, sesion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.estado").value("ABIERTO"))
            .andExpect(jsonPath("$.uid").doesNotExist());
        mvc.perform(post("/chat/tickets").header(CABECERA, sesion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.motivo").value("TICKET_ABIERTO"));
        // Su siguiente pregunta sin resolver se suma a esa.
        String respuesta = escribir(visitante(sesion), "zxqwv plorf", null);
        assertThat((Boolean) JsonPath.read(respuesta, "$.enriquecido.solicitud.nueva")).isFalse();
        mvc.perform(get("/chat/tickets").header(CABECERA, sesion))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].asunto").value("No me llega el correo"));
        // Un identificador que no es de visitante no abre nada.
        mvc.perform(post("/chat/tickets").header(CABECERA, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.motivo").value("SESION_REQUERIDA"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chatbot.tickets_soporte", Integer.class)).isEqualTo(1);
    }

    // ------------------------------------------------------------ auxiliares

    private static RequestPostProcessor visitante(String sesion) {
        return peticion -> {
            peticion.addHeader(CABECERA, sesion);
            return peticion;
        };
    }

    private String escribir(RequestPostProcessor quien, String texto, String vista) throws Exception {
        String cuerpo = vista == null
            ? "{\"contenido\":\"" + texto + "\"}"
            : "{\"contenido\":\"" + texto + "\",\"vista\":\"" + vista + "\"}";
        MockHttpServletRequestBuilder peticion = post("/chat/mensajes").with(quien)
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo);
        return mvc.perform(peticion)
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private List<String> contextoDe(String ticketId) {
        return jdbc.queryForList(
            "SELECT contenido FROM chatbot.tickets_soporte_contexto WHERE ticket_id = ?::uuid ORDER BY orden",
            String.class, ticketId);
    }
}
