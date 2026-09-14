package com.nexusbattles.ms_subastas.seguridad;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cubre el puente entre el token y el puerto {@code IdentidadClient} de
 * HU-SUB-001. Lo importante aqui no es que resuelva la identidad cuando todo
 * va bien, sino que FALLE cuando no puede determinar a quien cobrarle: por este
 * puerto pasan publicar, pujar y comprar, y las tres mueven creditos.
 */
class IdentidadDesdeTokenTest {

    private static final String CLAVE = "clave-de-prueba-suficientemente-larga-para-hmac-sha256-de-32-bytes";
    private static final Instant AHORA = Instant.parse("2026-09-13T10:00:00Z");

    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
    private final ValidadorDeToken validador = new ValidadorDeToken(CLAVE, reloj);

    @Test
    void resuelveElJugadorDesdeElClaimUid() {
        UUID jugador = UUID.fromString("11111111-2222-3333-4444-555555555555");
        var identidad = new IdentidadDesdeToken(validador, peticionCon("Bearer " + tokenCon(jugador)));

        var actual = identidad.actual();

        assertEquals(jugador, actual.usuarioId());
    }

    /**
     * El dato no viaja en el token y ms-identidad no tiene ese rol, asi que se
     * devuelve false: el Maestro de Juego esta exento de la comision de
     * publicacion, y suponerlo true regalaria exenciones. Limitacion
     * documentada, no olvido.
     */
    @Test
    void noPuedeSaberSiEsMaestroDeJuegoYAsumeQueNo() {
        var identidad = new IdentidadDesdeToken(validador, peticionCon("Bearer " + tokenCon(UUID.randomUUID())));

        assertFalse(identidad.actual().esMaestroDeJuego());
    }

    /**
     * El caso que preguntaba Edwin: token valido, firmado y sin expirar, pero
     * sin el claim uid. No se puede dejar pasar hacia una operacion que cobra
     * comision o retiene creditos.
     */
    @Test
    void unTokenValidoPeroSinUidSeRechaza() {
        String sinUid = Jwts.builder()
                .subject("andres_nv")
                .claim("rol", "JUGADOR")
                .claim("ver", 1)
                .expiration(Date.from(AHORA.plus(Duration.ofHours(1))))
                .signWith(claveDe(CLAVE))
                .compact();
        var identidad = new IdentidadDesdeToken(validador, peticionCon("Bearer " + sinUid));

        TokenInvalidoException error = assertThrows(TokenInvalidoException.class, identidad::actual);

        assertEquals("el token no trae identificador de jugador: vuelve a iniciar sesion", error.getMessage());
    }

    @Test
    void sinEncabezadoAuthorizationSeRechaza() {
        var identidad = new IdentidadDesdeToken(validador, peticionCon(null));

        assertThrows(TokenInvalidoException.class, identidad::actual);
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() {
        String ajeno = Jwts.builder()
                .subject("intruso")
                .claim("rol", "ADMINISTRADOR")
                .claim("ver", 1)
                .claim("uid", UUID.randomUUID().toString())
                .expiration(Date.from(AHORA.plus(Duration.ofHours(1))))
                .signWith(claveDe("otra-clave-distinta-igualmente-larga-para-hmac-sha256-de-32"))
                .compact();
        var identidad = new IdentidadDesdeToken(validador, peticionCon("Bearer " + ajeno));

        assertThrows(TokenInvalidoException.class, identidad::actual);
    }

    @Test
    void unTokenExpiradoSeRechaza() {
        String vencido = Jwts.builder()
                .subject("andres_nv")
                .claim("rol", "JUGADOR")
                .claim("ver", 1)
                .claim("uid", UUID.randomUUID().toString())
                .expiration(Date.from(AHORA.minus(Duration.ofMinutes(1))))
                .signWith(claveDe(CLAVE))
                .compact();
        var identidad = new IdentidadDesdeToken(validador, peticionCon("Bearer " + vencido));

        assertThrows(TokenInvalidoException.class, identidad::actual);
    }

    private static HttpServletRequest peticionCon(String encabezado) {
        HttpServletRequest peticion = mock(HttpServletRequest.class);
        when(peticion.getHeader("Authorization")).thenReturn(encabezado);
        return peticion;
    }

    private static String tokenCon(UUID jugador) {
        return Jwts.builder()
                .subject("andres_nv")
                .claim("rol", "JUGADOR")
                .claim("ver", 1)
                .claim("uid", jugador.toString())
                .issuedAt(Date.from(AHORA))
                .expiration(Date.from(AHORA.plus(Duration.ofHours(24))))
                .signWith(claveDe(CLAVE))
                .compact();
    }

    private static SecretKey claveDe(String texto) {
        return Keys.hmacShaKeyFor(texto.getBytes(StandardCharsets.UTF_8));
    }
}
