package com.nexusbattles.ms_chatbot.chat;

import com.jayway.jsonpath.JsonPath;
import com.nexusbattles.ms_chatbot.chat.consultas.InventarioClient;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.ElementoInventarioDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaInventarioDto;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ms-chatbot.yaml 1.18.0 (§7.4.4, observación de RF-CHA-008: «el acceso del
 * chatbot a datos personales debe respetar el modelo de autorización y quedar
 * auditado»), con PostgreSQL real y, en lugar de ms-identidad y
 * ms-cumplimiento, un emisor y una bitácora de mentira en un puerto local:
 *
 * <ul>
 *   <li>la consulta de una jugadora a su inventario deja un asiento en la
 *       bitácora central, con la credencial de servicio de ms-chatbot
 *       ({@code client_credentials}), su {@code uid} como quien pidió y como
 *       afectada, el dato, el resultado, su dirección y la traza de la
 *       pregunta, y <b>sin el contenido</b>;</li>
 *   <li>lo que no lee datos de nadie (un tema de la base, un visitante que
 *       pide lo suyo) no deja asiento.</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@DisplayName("Chatbot · el acceso del asistente a los datos de una persona queda auditado (1.18.0)")
class AccesosAuditadosIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final String RUTA_DE_LA_BITACORA = "/api/v1/admin/auditoria/eventos";
    private static final String TRAZA = "4bf92f3577b34da6a3ce929d0e0e4736";

    private record Recibida(String autorizacion, String traza, String cuerpo) {
    }

    private static final List<Recibida> ASIENTOS = new CopyOnWriteArrayList<>();
    private static final List<String> CREDENCIALES_PEDIDAS = new CopyOnWriteArrayList<>();
    private static HttpServer servidor;

    @DynamicPropertySource
    static void dependencias(DynamicPropertyRegistry propiedades) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/token", intercambio -> {
            CREDENCIALES_PEDIDAS.add(intercambio.getRequestHeaders().getFirst("Authorization"));
            responder(intercambio, 200,
                "{\"access_token\":\"token-del-asistente\",\"token_type\":\"Bearer\",\"expires_in\":300}");
        });
        servidor.createContext(RUTA_DE_LA_BITACORA, intercambio -> {
            ASIENTOS.add(new Recibida(intercambio.getRequestHeaders().getFirst("Authorization"),
                intercambio.getRequestHeaders().getFirst("traceparent"),
                new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            responder(intercambio, 201, "{}");
        });
        servidor.start();
        String base = "http://127.0.0.1:" + servidor.getAddress().getPort();
        propiedades.add("chatbot.auditoria.url", () -> base + RUTA_DE_LA_BITACORA);
        propiedades.add("seguridad.servicio.url", () -> base + "/token");
        propiedades.add("seguridad.servicio.client-id", () -> "ms-chatbot");
        propiedades.add("seguridad.servicio.client-secret", () -> "secreto-del-asistente");
    }

    private static void responder(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }

    @AfterAll
    static void apagar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Autowired
    private MockMvc mvc;

    // La lista negra es otro servicio: aqui aprueba todo.
    @MockitoBean
    private ModeracionDeContenido moderacion;

    @MockitoBean
    private InventarioClient inventario;

    @BeforeEach
    void limpiar() {
        ASIENTOS.clear();
    }

    private String preguntar(MockHttpServletRequestBuilder peticion, String texto) throws Exception {
        return mvc.perform(peticion
                .header("X-Real-IP", "203.0.113.9")
                .header("traceparent", "00-" + TRAZA + "-00f067aa0ba902b7-01")
                .contentType("application/json")
                .content("{\"contenido\":\"" + texto + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private static void esperarAsientos(int cuantos) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (ASIENTOS.size() < cuantos && System.nanoTime() < limite) {
            Thread.sleep(50);
        }
    }

    @Test
    @DisplayName("su inventario queda en la bitacora central, con la credencial del asistente y sin el contenido")
    void laConsultaASuInventarioQuedaEnLaBitacoraCentralSinElContenido() throws Exception {
        UUID uid = UUID.randomUUID();
        when(inventario.consultarInventario(anyString(), anyInt())).thenReturn(new PaginaInventarioDto(
            List.of(new ElementoInventarioDto("Espada del Alba", "ARMA", true)), 1));

        String respuesta = preguntar(post("/chat/mensajes")
            .with(jwt().jwt(j -> j.claim("uid", uid.toString()).claim("rol", "JUGADOR"))), "que tengo en mi inventario");

        assertThat((String) JsonPath.read(respuesta, "$.contenido")).contains("Espada del Alba");
        esperarAsientos(1);
        assertThat(ASIENTOS).hasSize(1);
        Recibida asiento = ASIENTOS.get(0);
        // Con la credencial de servicio de ms-chatbot, nunca con el token de la jugadora.
        assertThat(asiento.autorizacion()).isEqualTo("Bearer token-del-asistente");
        assertThat(CREDENCIALES_PEDIDAS).isNotEmpty().allSatisfy(basica -> assertThat(basica).isEqualTo("Basic "
            + Base64.getEncoder().encodeToString("ms-chatbot:secreto-del-asistente".getBytes(StandardCharsets.UTF_8))));
        // La traza de la pregunta llega a la bitacora (regla 5).
        assertThat(asiento.traza()).startsWith("00-" + TRAZA + "-");
        String cuerpo = asiento.cuerpo();
        assertThat((String) JsonPath.read(cuerpo, "$.tipoAccion")).isEqualTo("OTRO");
        assertThat((String) JsonPath.read(cuerpo, "$.administradorId")).isEqualTo(uid.toString());
        assertThat((String) JsonPath.read(cuerpo, "$.afectado")).isEqualTo(uid.toString());
        assertThat((String) JsonPath.read(cuerpo, "$.valorNuevo")).isEqualTo("dato=inventario;resultado=consultado");
        assertThat((String) JsonPath.read(cuerpo, "$.motivo")).isEqualTo("CONSULTA_DEL_ASISTENTE");
        assertThat((String) JsonPath.read(cuerpo, "$.ipOrigen")).isEqualTo("203.0.113.9");
        assertThat((String) JsonPath.read(cuerpo, "$.referencia")).startsWith("ms-chatbot:consulta-");
        // Ni la pregunta ni lo que respondio el inventario.
        assertThat(cuerpo).doesNotContain("Espada").doesNotContain("que tengo");
    }

    @Test
    @DisplayName("un tema de la base o un visitante que pide lo suyo no leen datos de nadie: no hay asiento")
    void loQueNoLeeDatosDeNadieNoDejaAsiento() throws Exception {
        UUID uid = UUID.randomUUID();
        preguntar(post("/chat/mensajes").with(jwt().jwt(j -> j.claim("uid", uid.toString()).claim("rol", "JUGADOR"))),
            "Como funciona el Torneo");
        String delVisitante = preguntar(post("/chat/mensajes"), "que tengo en mi inventario");

        assertThat((String) JsonPath.read(delVisitante, "$.contenido")).contains("Sin sesión");
        Thread.sleep(500);
        assertThat(ASIENTOS).isEmpty();
    }
}
