package com.nexusbattles.ms_subastas.seguridad;

import com.nexusbattles.ms_subastas.pujas.service.MutableClock;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El validador solo comprueba firma y expiracion: la verificacion de
 * revocacion de rol (claim "ver" contra la base de datos de ms-identidad)
 * quedo fuera de alcance por acuerdo con Cristian — ver README del servicio,
 * "Asunciones tomadas" punto 4. Estas pruebas fijan ese contrato, incluido lo
 * que NO hace, para que nadie asuma de mas al construir encima.
 *
 * Los tokens se generan aqui con la misma libreria y el mismo formato que
 * ms-identidad (apodo como sujeto, claims "rol" y "ver"), sin importar ni una
 * clase de ese servicio: ArchUnit lo prohibe y el formato es lo unico que
 * comparten.
 */
class ValidadorDeTokenTest {

    private static final String CLAVE = "clave-de-prueba-suficientemente-larga-para-hmac-sha256-de-32-bytes";
    private static final String OTRA_CLAVE = "otra-clave-distinta-igualmente-larga-para-hmac-sha256-de-32-bytes";
    private static final Instant AHORA = Instant.parse("2026-09-11T10:00:00Z");

    private final MutableClock reloj = new MutableClock(AHORA);
    private final ValidadorDeToken validador = new ValidadorDeToken(CLAVE, reloj);

    @Test
    void unTokenValidoDevuelveElApodoElRolYLaVersion() {
        String token = tokenFirmadoCon(CLAVE, "andres", "JUGADOR", 3, AHORA.plus(Duration.ofHours(24)));

        IdentidadDelSolicitante identidad = validador.validar(token);

        assertEquals("andres", identidad.apodo());
        assertEquals("JUGADOR", identidad.rol());
        assertEquals(3, identidad.versionToken());
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() {
        String token = tokenFirmadoCon(OTRA_CLAVE, "andres", "JUGADOR", 1, AHORA.plus(Duration.ofHours(1)));

        TokenInvalidoException error = assertThrows(TokenInvalidoException.class, () -> validador.validar(token));

        assertTrue(error.getMessage().toLowerCase().contains("firma"),
                "el motivo debe decir que la firma no valida, no filtrar el detalle de la libreria");
    }

    @Test
    void unTokenExpiradoSeRechaza() {
        String token = tokenFirmadoCon(CLAVE, "andres", "JUGADOR", 1, AHORA.plus(Duration.ofHours(24)));

        reloj.avanzar(Duration.ofHours(25));

        TokenInvalidoException error = assertThrows(TokenInvalidoException.class, () -> validador.validar(token));
        assertTrue(error.getMessage().toLowerCase().contains("expir"));
    }

    @Test
    void unTokenQueTodaviaNoExpiraSeAcepta() {
        String token = tokenFirmadoCon(CLAVE, "andres", "JUGADOR", 1, AHORA.plus(Duration.ofHours(24)));

        reloj.avanzar(Duration.ofHours(23).plusMinutes(59));

        assertEquals("andres", validador.validar(token).apodo());
    }

    @Test
    void unTokenSinRolSeRechazaPorqueSinRolNoSePuedeAutorizarNada() {
        String token = Jwts.builder()
                .subject("andres")
                .claim("ver", 1)
                .expiration(Date.from(AHORA.plus(Duration.ofHours(1))))
                .signWith(claveDe(CLAVE))
                .compact();

        TokenInvalidoException error = assertThrows(TokenInvalidoException.class, () -> validador.validar(token));
        assertTrue(error.getMessage().toLowerCase().contains("rol"));
    }

    @Test
    void unTokenSinVersionSeRechazaPorqueIncumpleElFormatoDeMsIdentidad() {
        String token = Jwts.builder()
                .subject("andres")
                .claim("rol", "JUGADOR")
                .expiration(Date.from(AHORA.plus(Duration.ofHours(1))))
                .signWith(claveDe(CLAVE))
                .compact();

        assertThrows(TokenInvalidoException.class, () -> validador.validar(token));
    }

    @Test
    void unTokenSinSujetoSeRechaza() {
        String token = Jwts.builder()
                .claim("rol", "JUGADOR")
                .claim("ver", 1)
                .expiration(Date.from(AHORA.plus(Duration.ofHours(1))))
                .signWith(claveDe(CLAVE))
                .compact();

        assertThrows(TokenInvalidoException.class, () -> validador.validar(token));
    }

    @Test
    void unTokenSinFirmarSeRechaza() {
        String sinFirma = Jwts.builder()
                .subject("andres")
                .claim("rol", "ADMINISTRADOR")
                .claim("ver", 1)
                .expiration(Date.from(AHORA.plus(Duration.ofHours(1))))
                .compact();

        assertThrows(TokenInvalidoException.class, () -> validador.validar(sinFirma));
    }

    @Test
    void unTokenAlteradoSeRechaza() {
        String token = tokenFirmadoCon(CLAVE, "andres", "JUGADOR", 1, AHORA.plus(Duration.ofHours(1)));
        String alterado = token.substring(0, token.lastIndexOf('.') + 1) + "firmaInventada";

        assertThrows(TokenInvalidoException.class, () -> validador.validar(alterado));
    }

    @Test
    void textoQueNoEsUnTokenSeRechaza() {
        assertThrows(TokenInvalidoException.class, () -> validador.validar("esto-no-es-un-jwt"));
    }

    @Test
    void unTokenNuloOEnBlancoSeRechaza() {
        assertThrows(TokenInvalidoException.class, () -> validador.validar(null));
        assertThrows(TokenInvalidoException.class, () -> validador.validar("   "));
    }

    @Test
    void elEncabezadoAuthorizationSeAceptaConElPrefijoBearer() {
        String token = tokenFirmadoCon(CLAVE, "andres", "JUGADOR", 7, AHORA.plus(Duration.ofHours(1)));

        IdentidadDelSolicitante identidad = validador.validarEncabezado("Bearer " + token);

        assertEquals("andres", identidad.apodo());
        assertEquals(7, identidad.versionToken());
    }

    @Test
    void elPrefijoBearerSeReconoceSinImportarMayusculas() {
        String token = tokenFirmadoCon(CLAVE, "andres", "JUGADOR", 1, AHORA.plus(Duration.ofHours(1)));

        assertEquals("andres", validador.validarEncabezado("bearer " + token).apodo());
    }

    @Test
    void unEncabezadoSinPrefijoBearerSeRechaza() {
        String token = tokenFirmadoCon(CLAVE, "andres", "JUGADOR", 1, AHORA.plus(Duration.ofHours(1)));

        TokenInvalidoException error = assertThrows(TokenInvalidoException.class,
                () -> validador.validarEncabezado(token));

        assertTrue(error.getMessage().toLowerCase().contains("bearer"));
    }

    @Test
    void unEncabezadoAusenteSeRechaza() {
        assertThrows(TokenInvalidoException.class, () -> validador.validarEncabezado(null));
        assertThrows(TokenInvalidoException.class, () -> validador.validarEncabezado(""));
    }

    private static String tokenFirmadoCon(String clave, String apodo, String rol, int version, Instant expira) {
        return Jwts.builder()
                .subject(apodo)
                .claim("rol", rol)
                .claim("ver", version)
                .issuedAt(Date.from(AHORA))
                .expiration(Date.from(expira))
                .signWith(claveDe(clave))
                .compact();
    }

    private static SecretKey claveDe(String texto) {
        return Keys.hmacShaKeyFor(texto.getBytes(StandardCharsets.UTF_8));
    }
}
