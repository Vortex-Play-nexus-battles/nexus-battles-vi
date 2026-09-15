package com.nexusbattles.ms_finanzas.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

class JwtServiceTest {

    private static final String CLAVE = "clave-de-pruebas-que-debe-tener-al-menos-32-bytes-para-hmac-sha256";
    private static final String OTRA_CLAVE = "una-clave-distinta-igualmente-de-32-bytes-para-forzar-firma-mala!";

    private final JwtService jwtService = new JwtService(CLAVE);

    private SecretKey clave(String texto) {
        return Keys.hmacShaKeyFor(texto.getBytes(StandardCharsets.UTF_8));
    }

    private String tokenFirmado(String claveTexto, Date emision, Date expiracion) {
        return Jwts.builder()
                .subject("jugador-1")
                .claim("rol", "JUGADOR")
                .claim("uid", UUID.randomUUID().toString())
                .claim("ver", 1)
                .issuedAt(emision)
                .expiration(expiracion)
                .signWith(clave(claveTexto))
                .compact();
    }

    @Test
    void validar_conFirmaYClaimsCorrectos_devuelveClaims() {
        Date ahora = new Date();
        Date luego = new Date(ahora.getTime() + 60_000);
        String token = tokenFirmado(CLAVE, ahora, luego);

        Claims claims = jwtService.validarYObtenerClaims(token);

        assertThat(claims.getSubject()).isEqualTo("jugador-1");
        assertThat(claims.get("rol", String.class)).isEqualTo("JUGADOR");
        assertThat(claims.get("uid", String.class)).isNotBlank();
    }

    @Test
    void validar_conFirmaDeOtraClave_lanzaSignatureException() {
        Date ahora = new Date();
        Date luego = new Date(ahora.getTime() + 60_000);
        String tokenDeOtro = tokenFirmado(OTRA_CLAVE, ahora, luego);

        assertThatThrownBy(() -> jwtService.validarYObtenerClaims(tokenDeOtro))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void validar_conTokenExpirado_lanzaExpiredJwtException() {
        Date hace1Hora = new Date(System.currentTimeMillis() - 3_600_000L);
        Date hace1Minuto = new Date(System.currentTimeMillis() - 60_000L);
        String tokenViejo = tokenFirmado(CLAVE, hace1Hora, hace1Minuto);

        assertThatThrownBy(() -> jwtService.validarYObtenerClaims(tokenViejo))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void validar_conTokenGarabateado_lanzaExcepcion() {
        assertThatThrownBy(() -> jwtService.validarYObtenerClaims("no-es-un-jwt-real"))
                .isInstanceOf(Exception.class);
    }
}
