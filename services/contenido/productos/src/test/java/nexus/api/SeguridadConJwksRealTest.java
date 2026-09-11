package nexus.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import nexus.persistencia.ProductoRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Seguridad con el decodificador REAL de Spring (sin JwtDecoder simulado): un
 * servidor HTTP minimo sirve un JWKS con una clave RSA generada aqui, igual
 * que el servicio jwks-dev de docker-compose.contenido.yml, y los tokens se
 * firman con esa clave. Fija el contrato del JWKS de desarrollo (kty, kid,
 * alg, use, n, e) y que SeguridadConfig lee los roles de realm_access.roles.
 * Nacio de las 8 aserciones rojas de Newman contra la instancia de contenido
 * el 9-sep: sin Keycloak desplegado no habia forma de obtener un token.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeguridadConJwksRealTest {

    private static HttpServer servidorJwks;
    private static RSAKey clave;

    @DynamicPropertySource
    static void publicarJwks(DynamicPropertyRegistry registro) throws Exception {
        clave = new RSAKeyGenerator(2048).keyID("jwks-dev-prueba")
                .keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).generate();
        byte[] jwks = new JWKSet(clave.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        servidorJwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidorJwks.createContext("/certs.json", intercambio -> {
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, jwks.length);
            try (var salida = intercambio.getResponseBody()) {
                salida.write(jwks);
            }
        });
        servidorJwks.start();
        registro.add("KEYCLOAK_JWK_SET_URI",
                () -> "http://127.0.0.1:" + servidorJwks.getAddress().getPort() + "/certs.json");
    }

    @AfterAll
    static void apagarServidor() {
        if (servidorJwks != null) {
            servidorJwks.stop(0);
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductoRepository productoRepository;

    private static String token(String rol) throws Exception {
        long ahora = System.currentTimeMillis();
        JWTClaimsSet cuerpo = new JWTClaimsSet.Builder()
                .issuer("jwks-dev").subject("cesar")
                .claim("preferred_username", "cesar")
                .claim("realm_access", Map.of("roles", List.of(rol)))
                .issueTime(new Date(ahora)).expirationTime(new Date(ahora + 3_600_000))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(clave.getKeyID()).build(), cuerpo);
        jwt.sign(new RSASSASigner(clave));
        return jwt.serialize();
    }

    @Test
    @DisplayName("un token RS256 firmado con la clave del JWKS y rol ADMINISTRADOR entra a las estadisticas")
    void tokenValidoConRolAdministrador() throws Exception {
        when(productoRepository.count()).thenReturn(3L);

        mvc.perform(get("/api/v1/productos/estadisticas")
                        .header("Authorization", "Bearer " + token("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    @DisplayName("un token valido con rol JUGADOR entra a las estadisticas (solo pide autenticacion) pero no puede crear productos (403)")
    void tokenValidoSinRolDeAdministrador() throws Exception {
        when(productoRepository.count()).thenReturn(0L);

        mvc.perform(get("/api/v1/productos/estadisticas")
                        .header("Authorization", "Bearer " + token("JUGADOR")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/productos").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token("JUGADOR"))
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un token firmado con otra clave no pasa (401)")
    void tokenDeOtraClave() throws Exception {
        RSAKey otra = new RSAKeyGenerator(2048).keyID(clave.getKeyID()).generate();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(otra.getKeyID()).build(),
                new JWTClaimsSet.Builder().subject("intruso")
                        .claim("realm_access", Map.of("roles", List.of("ADMINISTRADOR")))
                        .expirationTime(new Date(System.currentTimeMillis() + 60_000)).build());
        jwt.sign(new RSASSASigner(otra));

        mvc.perform(get("/api/v1/productos/estadisticas")
                        .header("Authorization", "Bearer " + jwt.serialize()))
                .andExpect(status().isUnauthorized());
    }
}
