package com.nexusbattles.ms_identidad.auth.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.ms_identidad.auth.controller.JwksController;
import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El endpoint habla el protocolo que espera {@code TokenDeServicioOAuth2}
 * (plataforma-seguridad): client_credentials con autenticacion basica del
 * cliente y respuesta RFC 6749 §5.1. Si aqui cambia la forma, todos los
 * clientes de servicio dejan de obtener credencial a la vez.
 */
@DisplayName("POST /api/v1/auth/token · client_credentials contra ms-identidad (ADR-005)")
class TokenControllerTest {

    private static final String SECRETO = "secreto-de-salas-partidas-16+";

    private final ClavesDeFirma claves = new ClavesDeFirma("");
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ClientesDeServicio clientes = new ClientesDeServicio("salas-partidas=" + SECRETO);
        EmisorDeTokensDeServicio emisor = new EmisorDeTokensDeServicio(claves, "ms-identidad", 15);
        mockMvc = MockMvcBuilders.standaloneSetup(new TokenController(clientes, emisor)).build();
    }

    private static String basic(String clientId, String secreto) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + secreto).getBytes(StandardCharsets.UTF_8));
    }

    private Claims verificar(String token) {
        return Jwts.parser().verifyWith(claves.publica()).build().parseSignedClaims(token).getPayload();
    }

    @Test
    @DisplayName("con client_credentials y Basic correcto responde el token con la forma de la RFC 6749")
    void emiteConBasic() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header(HttpHeaders.AUTHORIZATION, basic("salas-partidas", SECRETO))
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andReturn();

        JsonNode cuerpo = json.readTree(resultado.getResponse().getContentAsString());
        Claims claims = verificar(cuerpo.get("access_token").asText());
        assertAll(
                () -> assertEquals("salas-partidas", claims.getSubject()),
                () -> assertEquals("salas-partidas", claims.get("azp", String.class)),
                () -> assertEquals("SERVICIO", claims.get("rol", String.class))
        );
    }

    @Test
    @DisplayName("tambien admite client_id/client_secret en el cuerpo (client_secret_post)")
    void emiteConCredencialesEnElCuerpo() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", "salas-partidas")
                        .param("client_secret", SECRETO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isString());
    }

    @Test
    @DisplayName("el kid del token es el que publica el JWKS: cualquier servicio lo verifica sin configurar nada nuevo")
    void elKidCoincideConElJwks() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header(HttpHeaders.AUTHORIZATION, basic("salas-partidas", SECRETO))
                        .param("grant_type", "client_credentials"))
                .andReturn();
        String token = json.readTree(resultado.getResponse().getContentAsString()).get("access_token").asText();
        Object kidDelToken = Jwts.parser().verifyWith(claves.publica()).build()
                .parseSignedClaims(token).getHeader().get("kid");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> jwks = (List<Map<String, Object>>) new JwksController(claves).jwks().get("keys");

        assertEquals(jwks.get(0).get("kid"), kidDelToken);
    }

    @Test
    @DisplayName("secreto equivocado o cliente desconocido: 401 invalid_client, sin decir cual de los dos")
    void rechazaCredencialesMalas() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header(HttpHeaders.AUTHORIZATION, basic("salas-partidas", SECRETO + "x"))
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.error").value("invalid_client"));

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header(HttpHeaders.AUTHORIZATION, basic("desconocido", SECRETO))
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    @DisplayName("otro grant, sin grant o sin credencial: 400 con el codigo de la RFC")
    void rechazaPeticionesMalFormadas() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header(HttpHeaders.AUTHORIZATION, basic("salas-partidas", SECRETO))
                        .param("grant_type", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_grant_type"));

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header(HttpHeaders.AUTHORIZATION, basic("salas-partidas", SECRETO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header(HttpHeaders.AUTHORIZATION, "Basic esto-no-es-base64!!")
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    @DisplayName("un token de servicio no sirve como token de usuario: no trae uid y su rol no es de usuario")
    void noEsUnTokenDeUsuario() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header(HttpHeaders.AUTHORIZATION, basic("salas-partidas", SECRETO))
                        .param("grant_type", "client_credentials"))
                .andReturn();
        Claims claims = verificar(json.readTree(resultado.getResponse().getContentAsString())
                .get("access_token").asText());

        assertTrue(claims.get("uid") == null);
        assertTrue(java.util.Arrays.stream(com.nexusbattles.ms_identidad.rbac.model.Role.values())
                .noneMatch(rol -> rol.name().equals(claims.get("rol", String.class))));
    }
}
