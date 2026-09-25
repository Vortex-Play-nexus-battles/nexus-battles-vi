package com.nexusbattles.ms_chatbot.chat;

import com.jayway.jsonpath.JsonPath;
import com.nexusbattles.ms_chatbot.chat.moderacion.ContenidoBloqueado;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B11 — las pruebas NEGATIVAS del fallo de la auditoria, de punta a punta: la
 * aplicacion entera, PostgreSQL real con las migraciones V1-V5 y la cadena de
 * seguridad real.
 *
 * <p>El ataque exacto: una jugadora (uid publico, p. ej. en GET /torneos)
 * conversa con el chatbot; un visitante sin token manda
 * {@code X-Id-Sesion-Anonima: <uid de la jugadora>} e intenta leer, calificar,
 * escribir en y borrar su historial. Hasta la 1.1.0 lo lograba. Aqui tiene que
 * fallar todo, y la conversacion de la jugadora tiene que quedar intacta.
 */
@Testcontainers
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    // El limite de mensajes, bajo, para poder comprobarlo en la prueba.
    "chatbot.limite.mensajes-por-minuto=4"
})
@AutoConfigureMockMvc
@DisplayName("Chatbot · el historial de un jugador no lo abre nadie mas (B11)")
class SeguridadDelHistorialIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final String CABECERA = "X-Id-Sesion-Anonima";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // La lista negra es otro servicio: aqui aprueba todo salvo lo que la prueba marque.
    @MockitoBean
    private ModeracionDeContenido moderacion;

    private UUID victima;

    @BeforeEach
    void preparar() {
        victima = UUID.randomUUID();
    }

    private static RequestPostProcessor tokenDe(UUID uid) {
        return jwt().jwt(j -> j.claim("uid", uid.toString()).claim("rol", "JUGADOR"));
    }

    /** Envia un mensaje y devuelve el cuerpo de la respuesta del bot. */
    private String escribir(RequestPostProcessor quien, String sesion, String texto) throws Exception {
        var peticion = post("/chat/mensajes").contentType("application/json")
            .content("{\"contenido\":\"" + texto + "\"}");
        if (quien != null) {
            peticion = peticion.with(quien);
        }
        if (sesion != null) {
            peticion = peticion.header(CABECERA, sesion);
        }
        MvcResult resultado = mvc.perform(peticion).andExpect(status().isOk()).andReturn();
        return resultado.getResponse().getContentAsString();
    }

    private int mensajesDe(RequestPostProcessor quien) throws Exception {
        String cuerpo = mvc.perform(get("/chat/historial").with(quien)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        Integer cuantos = JsonPath.read(cuerpo, "$.length()");
        return cuantos;
    }

    @Test
    @DisplayName("el ataque exacto de la auditoria (uid ajeno en X-Id-Sesion-Anonima) no lee, no califica, no escribe y no borra")
    void ataqueDeLaAuditoria() throws Exception {
        String respuestaDeLaVictima = escribir(tokenDe(victima), null, "hola");
        String idDeLaRespuesta = JsonPath.read(respuestaDeLaVictima, "$.id");
        assertThat(mensajesDe(tokenDe(victima))).isEqualTo(2);
        String uidAjeno = victima.toString();

        // Leer: vacio.
        mvc.perform(get("/chat/historial").header(CABECERA, uidAjeno))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());

        // Calificar una respuesta del bot de la victima: 404.
        mvc.perform(post("/chat/mensajes/{id}/calificacion", idDeLaRespuesta)
                .header(CABECERA, uidAjeno)
                .contentType("application/json")
                .content("{\"util\":false}"))
            .andExpect(status().isNotFound());

        // Escribir: el servidor le da una sesion NUEVA; no escribe en la de la victima.
        MvcResult escrito = mvc.perform(post("/chat/mensajes")
                .header(CABECERA, uidAjeno)
                .contentType("application/json")
                .content("{\"contenido\":\"hola\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string(CABECERA, org.hamcrest.Matchers.startsWith("anon_")))
            .andReturn();
        assertThat(escrito.getResponse().getHeader(CABECERA)).isNotEqualTo(uidAjeno);

        // Borrar: 204, pero no borra nada de la victima.
        mvc.perform(delete("/chat/historial").header(CABECERA, uidAjeno))
            .andExpect(status().isNoContent());

        assertThat(mensajesDe(tokenDe(victima))).as("la conversacion de la victima sigue intacta").isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from chatbot.calificaciones c join chatbot.mensajes m on m.id = c.mensaje_id "
            + "join chatbot.conversaciones v on v.id = m.conversacion_id where v.identificador_sesion = ?", Integer.class,
            victima.toString())).isZero();
    }

    @Test
    @DisplayName("una sesion legitima de visitante solo ve lo suyo; el visitante no ve la conversacion del jugador")
    void sesionLegitimaSoloVeLoSuyo() throws Exception {
        escribir(tokenDe(victima), null, "soy la jugadora");

        String sesion = mvc.perform(post("/chat/sesiones")).andExpect(status().isCreated())
            .andReturn().getResponse().getHeader(CABECERA);
        assertThat(sesion).matches("^anon_[A-Za-z0-9_-]{43}$");
        escribir(null, sesion, "soy el visitante");

        String historial = mvc.perform(get("/chat/historial").header(CABECERA, sesion))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(historial).contains("soy el visitante").doesNotContain("soy la jugadora");

        // Con token manda el token: la sesion del visitante en la cabecera se ignora.
        String delJugador = mvc.perform(get("/chat/historial").with(tokenDe(victima)).header(CABECERA, sesion))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(delJugador).contains("soy la jugadora").doesNotContain("soy el visitante");

        // En la base solo queda la huella del identificador, nunca el identificador.
        assertThat(jdbc.queryForObject("select count(*) from chatbot.sesiones_anonimas where huella = ?", Integer.class, sesion))
            .isZero();
        assertThat(jdbc.queryForObject("select count(*) from chatbot.conversaciones where identificador_sesion = ?", Integer.class,
            sesion)).isZero();
    }

    @Test
    @DisplayName("un identificador inventado por el cliente no abre nada y el primer mensaje recibe una sesion emitida")
    void identificadorInventado() throws Exception {
        String inventado = "visitante-" + UUID.randomUUID();
        mvc.perform(get("/chat/historial").header(CABECERA, inventado))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(post("/chat/mensajes").header(CABECERA, inventado)
                .contentType("application/json").content("{\"contenido\":\"hola\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string(CABECERA, org.hamcrest.Matchers.matchesPattern("^anon_[A-Za-z0-9_-]{43}$")));
    }

    @Test
    @DisplayName("la base impide que una conversacion de visitante tenga por clave un uid")
    void espacioDeClavesEnLaBase() {
        assertThatThrownBy(() -> jdbc.update("insert into chatbot.conversaciones (id, identificador_sesion, autenticado, "
                + "fecha_inicio, fecha_ultima_actividad) values (?, ?, false, now(), now())",
            UUID.randomUUID(), UUID.randomUUID().toString()))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into chatbot.conversaciones (id, identificador_sesion, autenticado, "
                + "fecha_inicio, fecha_ultima_actividad) values (?, ?, true, now(), now())",
            UUID.randomUUID(), "anonimo:" + UUID.randomUUID()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("limite de frecuencia por usuario: el quinto mensaje del minuto recibe 429 con Retry-After")
    void limiteDeFrecuencia() throws Exception {
        UUID jugador = UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            escribir(tokenDe(jugador), null, "hola " + i);
        }
        mvc.perform(post("/chat/mensajes").with(tokenDe(jugador))
                .contentType("application/json").content("{\"contenido\":\"otra vez\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.motivo").value("LIMITE_DE_FRECUENCIA"));
        assertThat(mensajesDe(tokenDe(jugador))).as("el mensaje rechazado no se guardo").isEqualTo(8);
        // El limite es por identidad: otro jugador sigue pudiendo escribir.
        escribir(tokenDe(UUID.randomUUID()), null, "hola");
    }

    @Test
    @DisplayName("un mensaje bloqueado por la lista negra responde 422 y no deja rastro")
    void mensajeBloqueado() throws Exception {
        UUID jugador = UUID.randomUUID();
        doThrow(new ContenidoBloqueado()).when(moderacion).verificar(anyString());

        mvc.perform(post("/chat/mensajes").with(tokenDe(jugador))
                .contentType("application/json").content("{\"contenido\":\"algo prohibido\"}"))
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.motivo").value("CONTENIDO_BLOQUEADO"));

        assertThat(mensajesDe(tokenDe(jugador))).isZero();
        assertThat(jdbc.queryForObject("select count(*) from chatbot.mensajes where contenido = 'algo prohibido'", Integer.class))
            .isZero();
    }
}
