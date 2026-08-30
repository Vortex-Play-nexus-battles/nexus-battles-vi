package nexus.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

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
}
