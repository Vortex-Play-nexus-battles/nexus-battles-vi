package com.nexusbattles.ms_chatbot.chat.catalogo;

import com.jayway.jsonpath.JsonPath;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1.10.0 de punta a punta: un visitante pregunta un precio y la respuesta sale
 * del catálogo (un servidor HTTP de verdad que sirve la forma de
 * {@code productos.yaml}), se guarda como consulta en vivo y el catálogo nunca
 * recibe una credencial.
 */
@Testcontainers
// Plazos de la llamada al catalogo holgados: aqui se prueba el camino, no la
// latencia, y una maquina cargada no debe convertir el precio en «la base
// responde» (paso una vez en local, con otra compilacion corriendo al lado).
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "app.http.timeout-conexion-ms=3000",
    "app.http.timeout-lectura-ms=5000"})
@AutoConfigureMockMvc
@DisplayName("Chatbot · catalogo en vivo de punta a punta (1.10.0)")
class CatalogoEnVivoIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final List<String> AUTORIZACIONES_RECIBIDAS = new CopyOnWriteArrayList<>();
    // 1.13.0: un recurso grafico del catalogo pasa a la tarjeta; un nombre suelto, no.
    private static final String IMAGEN = "/api/v1/productos/recursos/6f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f";
    private static final HttpServer CATALOGO = catalogo();

    private static HttpServer catalogo() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/api/v1/productos", intercambio -> {
                String autorizacion = intercambio.getRequestHeaders().getFirst("Authorization");
                AUTORIZACIONES_RECIBIDAS.add(autorizacion == null ? "ninguna" : autorizacion);
                byte[] cuerpo = ("{\"content\":[{\"id\":\"p-1\",\"nombre\":\"Espada de una mano\",\"tipo\":\"ARMA\","
                    + "\"descripcion\":\"+1 ataque\",\"precioCreditos\":120,\"premium\":false,\"estado\":\"ACTIVO\","
                    + "\"imagen\":\"" + IMAGEN + "\"},"
                    + "{\"id\":\"p-2\",\"nombre\":\"Magma Ardiente\",\"tipo\":\"ARMADURA\",\"descripcion\":\"+2 defensa\","
                    + "\"precioCreditos\":80,\"premium\":false,\"estado\":\"ACTIVO\",\"imagen\":\"magma.png\"}],"
                    + "\"page\":0,\"size\":50,\"totalElements\":2,\"totalPages\":1}").getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, cuerpo.length);
                try (OutputStream salida = intercambio.getResponseBody()) {
                    salida.write(cuerpo);
                }
            });
            servidor.start();
            return servidor;
        } catch (IOException fallo) {
            throw new IllegalStateException(fallo);
        }
    }

    @DynamicPropertySource
    static void urlDelCatalogo(DynamicPropertyRegistry propiedades) {
        propiedades.add("app.productos.url", () -> "http://127.0.0.1:" + CATALOGO.getAddress().getPort());
    }

    @AfterAll
    static void apagarCatalogo() {
        CATALOGO.stop(0);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ModeracionDeContenido moderacion;

    @Test
    @DisplayName("un visitante pregunta un precio y responde el catalogo, sin credencial")
    void elPrecioSaleDelCatalogo() throws Exception {
        String visitante = "visitante-" + UUID.randomUUID();

        String respuesta = mvc.perform(post("/chat/mensajes").header("X-Id-Sesion-Anonima", visitante)
                .contentType("application/json").content("{\"contenido\":\"¿Cuánto cuesta la espada de una mano?\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(respuesta, "$.contenido"))
            .isEqualTo("«Espada de una mano» (arma) cuesta 120 créditos. Lo encuentras en la «Tienda».");
        List<String> destinos = JsonPath.read(respuesta, "$.enriquecido.enlaces[*].destino");
        assertThat(destinos).containsExactly("tienda");
        // 1.13.0 (§7.4.3): la tarjeta del producto, con la imagen del catalogo.
        assertThat((String) JsonPath.read(respuesta, "$.enriquecido.tarjetas[0].titulo")).isEqualTo("Espada de una mano");
        assertThat((String) JsonPath.read(respuesta, "$.enriquecido.tarjetas[0].imagen")).isEqualTo(IMAGEN);
        assertThat(jdbc.queryForObject(
            "SELECT resultado FROM chatbot.mensajes WHERE remitente = 'BOT' AND contenido LIKE '«Espada de una mano»%'",
            String.class)).isEqualTo("CONSULTA_EN_VIVO");
        assertThat(AUTORIZACIONES_RECIBIDAS).isNotEmpty().containsOnly("ninguna");
    }

    @Test
    @DisplayName("1.13.0: el listado lleva una tarjeta por producto; una imagen que nadie sirve no pasa")
    void elListadoLlevaTarjetasConLaImagenSoloSiEsDelCatalogo() throws Exception {
        String respuesta = mvc.perform(post("/chat/mensajes").header("X-Id-Sesion-Anonima", "visitante-" + UUID.randomUUID())
                .contentType("application/json").content("{\"contenido\":\"¿Qué productos hay en la tienda?\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        List<String> titulos = JsonPath.read(respuesta, "$.enriquecido.tarjetas[*].titulo");
        assertThat(titulos).containsExactly("Espada de una mano", "Magma Ardiente");
        List<Object> imagenes = JsonPath.read(respuesta, "$.enriquecido.tarjetas[*].imagen");
        assertThat(imagenes).containsExactly(IMAGEN);
        // Y se guarda igual: el historial la ensena con su tarjeta.
        assertThat(jdbc.queryForObject("SELECT enriquecido FROM chatbot.mensajes WHERE remitente = 'BOT' "
            + "AND contenido LIKE 'En el catálogo hay 2 productos%'", String.class)).contains(IMAGEN)
            .doesNotContain("magma.png");
    }

    @Test
    @DisplayName("una pregunta de como comprar la sigue respondiendo la base")
    void laOrientacionNoVaAlCatalogo() throws Exception {
        int consultasAntes = AUTORIZACIONES_RECIBIDAS.size();

        String respuesta = mvc.perform(post("/chat/mensajes").header("X-Id-Sesion-Anonima", "visitante-" + UUID.randomUUID())
                .contentType("application/json").content("{\"contenido\":\"¿Cómo compro en la tienda?\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(respuesta, "$.contenido")).doesNotContain("cuesta 120");
        assertThat(AUTORIZACIONES_RECIBIDAS).hasSize(consultasAntes);
    }
}
