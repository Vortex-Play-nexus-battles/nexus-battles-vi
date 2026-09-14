package com.nexusbattles.ms_identidad.auth;

import java.util.UUID;

import com.nexusbattles.ms_identidad.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    private static final String CLAVE_SECRETA_PRUEBA =
        "clave-de-pruebas-suficientemente-larga-para-hmac-sha";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "claveSecretaTexto", CLAVE_SECRETA_PRUEBA);
        ReflectionTestUtils.setField(jwtService, "horasExpiracion", 24);
    }

    @Test
    void debeGenerarUnTokenNoNuloYConLaEstructuraEsperada() {

        String token = jwtService.generarToken("cristianc", "JUGADOR", 0, UUID.randomUUID());

        assertNotNull(token);
        // Un JWT siempre tiene 3 partes separadas por puntos: header.payload.firma
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void debeValidarYDevolverElApodoYRolCorrectos() {

        String token = jwtService.generarToken("cristianc", "JUGADOR", 0, UUID.randomUUID());

        Claims claims = jwtService.validarYObtenerClaims(token);

        assertEquals("cristianc", claims.getSubject());
        assertEquals("JUGADOR", claims.get("rol", String.class));
    }

    @Test
    void debeRechazarUnTokenAlteradoOInvalido() {

        String token = jwtService.generarToken("cristianc", "JUGADOR", 0, UUID.randomUUID());
        // Se altera el último caracter de la firma, simulando una manipulación.
        String tokenAlterado = token.substring(0, token.length() - 1) + (token.endsWith("X") ? "Y" : "X");

        assertThrows(
            JwtException.class,
            () -> jwtService.validarYObtenerClaims(tokenAlterado)
        );
    }

    @Test
    void debeRechazarUnTokenCompletamenteMalformado() {

        assertThrows(
            JwtException.class,
            () -> jwtService.validarYObtenerClaims("esto-no-es-un-token-valido")
        );
    }

    @Test
    void debeConsiderarVigenteUnTokenConLaMismaVersion() {

        String token = jwtService.generarToken("cristianc", "JUGADOR", 3, UUID.randomUUID());
        Claims claims = jwtService.validarYObtenerClaims(token);

        assertTrue(jwtService.esVersionVigente(claims, 3));
    }

    @Test
    void debeRechazarComoNoVigenteUnTokenConVersionDesactualizada() {

        // Token generado cuando el usuario tenía versión 1 (antes de un
        // cambio de rol), comparado contra la versión actual (2).
        String token = jwtService.generarToken("cristianc", "JUGADOR", 1, UUID.randomUUID());
        Claims claims = jwtService.validarYObtenerClaims(token);

        assertFalse(jwtService.esVersionVigente(claims, 2));
    }

    @Test
    void debeIncluirElIdentificadorPublicoEstableComoClaimUid() {
        UUID publico = UUID.fromString("11111111-2222-3333-4444-555555555555");

        String token = jwtService.generarToken("cristianc", "JUGADOR", 0, publico);
        Claims claims = jwtService.validarYObtenerClaims(token);

        assertEquals(publico.toString(), claims.get("uid", String.class));
    }

    /**
     * El sujeto sigue siendo el apodo a proposito. SecurityInterceptor lo lee
     * como identidad y lo propaga a los controladores de administracion como
     * `usuarioActual`; cambiarlo por el UUID romperia esa cadena. El claim
     * `uid` se suma, no sustituye.
     */
    @Test
    void debeMantenerElApodoComoSujetoParaNoRomperElInterceptor() {
        String token = jwtService.generarToken("cristianc", "JUGADOR", 0,
            UUID.fromString("11111111-2222-3333-4444-555555555555"));

        Claims claims = jwtService.validarYObtenerClaims(token);

        assertEquals("cristianc", claims.getSubject());
        assertEquals("JUGADOR", claims.get("rol", String.class));
    }

    /**
     * Un usuario creado antes de que existiera el campo puede no tener UUID
     * todavia. El token se emite igual: quien lo consuma vera el claim ausente
     * y decidira, en vez de fallar el login.
     */
    @Test
    void sinIdentificadorPublicoElTokenSeEmiteSinEseClaim() {
        String token = jwtService.generarToken("cristianc", "JUGADOR", 0, null);

        Claims claims = jwtService.validarYObtenerClaims(token);

        assertEquals("cristianc", claims.getSubject());
        assertNull(claims.get("uid", String.class));
    }
}
