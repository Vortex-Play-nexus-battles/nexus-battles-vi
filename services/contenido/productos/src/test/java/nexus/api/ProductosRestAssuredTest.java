package nexus.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "KEYCLOAK_JWK_SET_URI=http://localhost/prueba/jwks")
class ProductosRestAssuredTest {

        @LocalServerPort
        private int puerto;

        @MockitoBean
        private JwtDecoder jwtDecoder;

        @MockitoBean
        private ProductoRepository productoRepository;

        @Test
        void verificaLaApiDesdeUnServidorHttpReal() {
                given()
                        .port(puerto)
                        .contentType("application/json")
                        .body("{}")
                .when()
                        .post("/api/v1/productos")
                .then()
                        .statusCode(401)
                        .contentType("application/problem+json")
                        .body("type", equalTo("urn:nexus:problema:no-autenticado"))
                        .body("status", equalTo(401));
        }

        @Test
        void consultaUnProductoExistenteSinAutenticacionDesdeUnServidorHttpReal() {
                String id = UUID.randomUUID().toString();
                Instant ahora = Instant.now();
                Producto producto = new Producto(
                        id,
                        "Espada solar",
                        "productos/espada-solar.webp",
                        "Arma creada para verificar la consulta",
                        TipoProducto.ARMA,
                        100,
                        500,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        40,
                        new BigDecimal("12.5"),
                        EstadoProducto.ACTIVO,
                        1,
                        ahora,
                        ahora);

                when(productoRepository.findById(id)).thenReturn(Optional.of(producto));

                given()
                        .port(puerto)
                .when()
                        .get("/api/v1/productos/" + id)
                .then()
                        .statusCode(200)
                        .contentType("application/json")
                        .body("id", equalTo(id))
                        .body("nombre", equalTo("Espada solar"))
                        .body("tipo", equalTo("ARMA"));
        }

        @Test
        void consultaUnProductoInexistenteDevuelveProblemDetailsDesdeUnServidorHttpReal() {
                String id = UUID.randomUUID().toString();
                when(productoRepository.findById(id)).thenReturn(Optional.empty());

                given()
                        .port(puerto)
                .when()
                        .get("/api/v1/productos/" + id)
                .then()
                        .statusCode(404)
                        .contentType("application/problem+json")
                        .body("type", equalTo("urn:nexus:problema:producto-no-encontrado"))
                        .body("status", equalTo(404));
        }
}
