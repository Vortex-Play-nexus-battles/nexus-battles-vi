package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auth.controller.JwksController;
import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La clave publicada tiene que servir de verdad para verificar los tokens que
 * emite este servicio. Es el contrato del que depende toda la plataforma desde
 * ADR-002: si el JWKS y la firma se separan, cada servicio devuelve 401 a
 * usuarios legitimos y el sintoma aparece lejos de la causa.
 *
 * <p>La prueba reconstruye la clave publica a partir del JSON del JWKS, como
 * haria Spring Security, y verifica con ella un token recien emitido.
 */
class JwksTest {

    private ClavesDeFirma claves;
    private JwtService jwtService;
    private JwksController controlador;

    @BeforeEach
    void setUp() {
        claves = new ClavesDeFirma("");
        jwtService = new JwtService(claves);
        ReflectionTestUtils.setField(jwtService, "horasExpiracion", 24);
        ReflectionTestUtils.setField(jwtService, "emisor", "ms-identidad");
        controlador = new JwksController(claves);
    }

    @Test
    @DisplayName("el JWKS describe una clave RSA de firma con su identificador")
    void formaDelJwks() {
        Map<String, Object> jwks = controlador.jwks();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> claves = (List<Map<String, Object>>) jwks.get("keys");
        assertEquals(1, claves.size());
        Map<String, Object> clave = claves.get(0);

        assertAll(
                () -> assertEquals("RSA", clave.get("kty")),
                () -> assertEquals("sig", clave.get("use")),
                () -> assertEquals("RS256", clave.get("alg")),
                () -> assertNotNull(clave.get("kid")),
                () -> assertNotNull(clave.get("n")),
                () -> assertNotNull(clave.get("e"))
        );
    }

    @Test
    @DisplayName("la clave del JWKS verifica un token emitido por el servicio")
    void laClavePublicadaVerificaElToken() throws Exception {
        UUID identificador = UUID.randomUUID();
        String token = jwtService.generarToken("cristianc", "JUGADOR", 0, identificador);

        Claims claims = Jwts.parser()
                .verifyWith(clavePublicaDelJwks())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertAll(
                () -> assertEquals("cristianc", claims.getSubject()),
                () -> assertEquals("JUGADOR", claims.get("rol")),
                () -> assertEquals(identificador.toString(), claims.get("uid")),
                () -> assertEquals("ms-identidad", claims.getIssuer())
        );
    }

    @Test
    @DisplayName("el kid del token es el que anuncia el JWKS")
    void elKidCoincide() {
        String token = jwtService.generarToken("cristianc", "JUGADOR", 0, UUID.randomUUID());
        String cabecera = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]));

        assertTrue(cabecera.contains(claves.identificador()),
                "el kid de la cabecera debe permitir elegir la clave del JWKS: " + cabecera);
    }

    /** Reconstruye la clave publica desde el JSON del JWKS, como un verificador. */
    private java.security.interfaces.RSAPublicKey clavePublicaDelJwks() throws Exception {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lista = (List<Map<String, Object>>) controlador.jwks().get("keys");
        Map<String, Object> clave = lista.get(0);

        BigInteger modulo = new BigInteger(1, Base64.getUrlDecoder().decode((String) clave.get("n")));
        BigInteger exponente = new BigInteger(1, Base64.getUrlDecoder().decode((String) clave.get("e")));

        PublicKey publica = KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(modulo, exponente));
        return (java.security.interfaces.RSAPublicKey) publica;
    }
}
