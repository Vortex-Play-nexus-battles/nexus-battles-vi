package com.nexusbattles.ms_ecommerce;

import com.jayway.jsonpath.JsonPath;
import com.nexusbattles.ms_ecommerce.seguridad.TokensDePrueba;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.ESCUDO;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.ESPADA;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.json;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.pagina;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * La tienda sobre el catalogo maestro con todo de verdad menos el servicio
 * productos: PostgreSQL con Flyway (V1 + V2) y Hibernate en {@code validate},
 * Tomcat, la cadena de seguridad, el cliente HTTP con sus tiempos de espera y
 * Jackson en las dos direcciones. El catalogo es un servidor HTTP del JDK que
 * contesta lo que contestaria el servicio productos.
 *
 * <p>Lo que solo esta prueba demuestra: que la instantanea del carrito se
 * escribe en las columnas nuevas de V2 y se vuelve a leer de ellas, y que la
 * vitrina publica llega entera desde un catalogo HTTP real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(TokensDePrueba.Decodificador.class)
@DisplayName("La tienda sobre el catalogo maestro, con PostgreSQL y HTTP de verdad")
class TiendaConCatalogoMaestroIT {

    private static final String SUSPENDIDO = "0f1e2d3c-4b5a-4968-8776-655443322110";
    private static final String CATALOGO_ROTO = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BASE = new PostgreSQLContainer<>("postgres:15-alpine");

    private static final HttpServer CATALOGO = catalogoDePrueba();

    @DynamicPropertySource
    static void catalogo(DynamicPropertyRegistry registro) {
        registro.add("catalogo.productos.url", () -> "http://127.0.0.1:" + CATALOGO.getAddress().getPort());
    }

    @AfterAll
    static void apagarCatalogo() {
        CATALOGO.stop(0);
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Lo que publicaria el servicio productos: un listado con dos productos en
     * venta, uno que solo se vende en creditos y uno agotado; el detalle de
     * cada uno; un producto suspendido; uno que hace fallar al catalogo, y 404
     * para todo lo demas.
     */
    private static HttpServer catalogoDePrueba() {
        Map<String, String> productos = Map.of(
                ESPADA, json(ESPADA, "Espada de fuego", "ARMA", "ACTIVO", 5, "6000.00"),
                ESCUDO, json(ESCUDO, "Escudo de roble", "ARMADURA", "UNICO", -1, "4000"),
                SUSPENDIDO, json(SUSPENDIDO, "Hacha retirada", "ARMA", "SUSPENDIDO", 3, "5000"));
        String listado = pagina(0, 1,
                productos.get(ESPADA),
                json("solo-creditos", "Pocion", "ITEM", "ACTIVO", -1, null),
                json("agotado", "Casco", "ARMADURA", "ACTIVO", 0, "3000"),
                productos.get(ESCUDO));
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/api/v1/productos", intercambio -> {
                String ruta = intercambio.getRequestURI().getPath();
                if (ruta.equals("/api/v1/productos")) {
                    responder(intercambio, 200, listado);
                    return;
                }
                String id = ruta.substring("/api/v1/productos/".length());
                if (id.equals(CATALOGO_ROTO)) {
                    responder(intercambio, 500, "{\"status\":500}");
                } else if (productos.containsKey(id)) {
                    responder(intercambio, 200, productos.get(id));
                } else {
                    responder(intercambio, 404, "{\"status\":404}");
                }
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void responder(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }

    private RestClient cliente() {
        return RestClient.create("http://localhost:" + puerto);
    }

    private static String tokenNuevo(UUID uid) {
        return "Bearer " + TokensDePrueba.deJugador("jugador-" + uid.toString().substring(0, 8), uid);
    }

    private ResponseEntity<String> agregar(String token, String cuerpo) {
        return cliente().post().uri("/ecommerce/api/v1/carrito/items")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .onStatus(estado -> true, (peticion, respuesta) -> { })
                .toEntity(String.class);
    }

    @Test
    @DisplayName("la vitrina es publica y proyecta solo lo que se vende, en COP y en el orden del catalogo")
    void vitrinaPublica() {
        ResponseEntity<String> respuesta = cliente().get().uri("/ecommerce/api/v1/vitrina")
                .retrieve().toEntity(String.class);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        String cuerpo = respuesta.getBody();
        List<String> ids = JsonPath.read(cuerpo, "$.content[*].id");
        assertThat(ids).containsExactly(ESPADA, ESCUDO);
        assertThat((String) JsonPath.read(cuerpo, "$.content[0].nombre")).isEqualTo("Espada de fuego");
        assertThat((String) JsonPath.read(cuerpo, "$.content[0].moneda")).isEqualTo("COP");
        assertThat(JsonPath.<Number>read(cuerpo, "$.content[0].precioFinal").doubleValue()).isEqualTo(6000.0);
        assertThat(JsonPath.<Number>read(cuerpo, "$.totalElements").intValue()).isEqualTo(2);
        assertThat((Boolean) JsonPath.read(cuerpo, "$.last")).isTrue();
    }

    @Test
    @DisplayName("agregar guarda la instantanea en las columnas de V2 y el carrito la devuelve desde la base")
    void agregarGuardaLaInstantanea() {
        UUID uid = UUID.randomUUID();
        String token = tokenNuevo(uid);

        ResponseEntity<String> agregado = agregar(token, "{\"productoId\":\"" + ESPADA + "\",\"cantidad\":2}");

        assertThat(agregado.getStatusCode().value()).isEqualTo(200);
        Number idDeLaLinea = JsonPath.read(agregado.getBody(), "$.items[0].id");
        assertThat(idDeLaLinea).as("la linea ya tiene id en la respuesta").isNotNull();

        String carrito = cliente().get().uri("/ecommerce/api/v1/carrito")
                .header(HttpHeaders.AUTHORIZATION, token)
                .retrieve().body(String.class);
        assertThat((String) JsonPath.read(carrito, "$.usuarioId")).isEqualTo(uid.toString());
        assertThat((String) JsonPath.read(carrito, "$.items[0].producto.id")).isEqualTo(ESPADA);
        assertThat((String) JsonPath.read(carrito, "$.items[0].producto.nombre")).isEqualTo("Espada de fuego");
        assertThat((String) JsonPath.read(carrito, "$.items[0].producto.moneda")).isEqualTo("COP");
        assertThat(JsonPath.<Number>read(carrito, "$.items[0].cantidad").intValue()).isEqualTo(2);
        assertThat(JsonPath.<Number>read(carrito, "$.items[0].precioUnitario").doubleValue()).isEqualTo(6000.0);
        assertThat(JsonPath.<Number>read(carrito, "$.total").doubleValue()).isEqualTo(12000.0);
        assertThat((String) JsonPath.read(carrito, "$.moneda")).isEqualTo("COP");

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT i.producto_ref, i.producto_nombre, i.moneda, i.producto_id FROM items_carrito i "
                        + "JOIN carritos c ON c.id = i.carrito_id WHERE c.usuario_id = ?", uid.toString());
        assertThat(fila)
                .containsEntry("producto_ref", ESPADA)
                .containsEntry("producto_nombre", "Espada de fuego")
                .containsEntry("moneda", "COP")
                .containsEntry("producto_id", null);
    }

    @Test
    @DisplayName("un producto suspendido en el catalogo: 409 producto-no-disponible")
    void suspendidoEs409() {
        ResponseEntity<String> respuesta = agregar(tokenNuevo(UUID.randomUUID()),
                "{\"productoId\":\"" + SUSPENDIDO + "\",\"cantidad\":1}");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(respuesta.getHeaders().getContentType()).isNotNull()
                .matches(tipo -> tipo.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat((String) JsonPath.read(respuesta.getBody(), "$.type"))
                .isEqualTo("urn:nexus:problema:producto-no-disponible");
    }

    @Test
    @DisplayName("un productoId numerico llega al catalogo como texto y se responde 422 producto-inexistente")
    void idNumericoEs422() {
        ResponseEntity<String> respuesta = agregar(tokenNuevo(UUID.randomUUID()), "{\"productoId\":1,\"cantidad\":1}");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(422);
        assertThat((String) JsonPath.read(respuesta.getBody(), "$.type"))
                .isEqualTo("urn:nexus:problema:producto-inexistente");
    }

    @Test
    @DisplayName("si el catalogo falla, 503 catalogo-no-disponible con Retry-After, no un 500")
    void catalogoRotoEs503() {
        ResponseEntity<String> respuesta = agregar(tokenNuevo(UUID.randomUUID()),
                "{\"productoId\":\"" + CATALOGO_ROTO + "\",\"cantidad\":1}");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(503);
        assertThat(respuesta.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
        assertThat((String) JsonPath.read(respuesta.getBody(), "$.type"))
                .isEqualTo("urn:nexus:problema:catalogo-no-disponible");
    }
}
