package com.nexusbattles.ms_identidad.auth;

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

        String token = jwtService.generarToken("cristianc", "JUGADOR");

        assertNotNull(token);
        // Un JWT siempre tiene 3 partes separadas por puntos: header.payload.firma
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void debeValidarYDevolverElApodoYRolCorrectos() {

        String token = jwtService.generarToken("cristianc", "JUGADOR");

        Claims claims = jwtService.validarYObtenerClaims(token);

        assertEquals("cristianc", claims.getSubject());
        assertEquals("JUGADOR", claims.get("rol", String.class));
    }

    @Test
    void debeRechazarUnTokenAlteradoOInvalido() {

        String token = jwtService.generarToken("cristianc", "JUGADOR");
        // Se altera el último caracter de la firma, simulando una manipulación.
        String tokenAlterado = token.substring(0, token.length() - 1) + "X";

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
}
