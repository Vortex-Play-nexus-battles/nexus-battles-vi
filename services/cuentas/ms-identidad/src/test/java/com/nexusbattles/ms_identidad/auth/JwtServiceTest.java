package com.nexusbattles.ms_identidad.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        // Par RSA efímero, el mismo camino que sigue el servicio cuando no se
        // configura app.jwt.clave-privada (ver ClavesDeFirma y ADR-002).
        jwtService = new JwtService(new ClavesDeFirma(""));
        ReflectionTestUtils.setField(jwtService, "horasExpiracion", 24);
        ReflectionTestUtils.setField(jwtService, "emisor", "ms-identidad");
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
        // Se altera el PAYLOAD, no el último caracter de la firma.
        //
        // Alterar ese último caracter no basta con RS256: la firma ocupa 256
        // bytes, que en base64url terminan en un caracter cuyos bits altos son
        // relleno, así que cambiarlo puede decodificar exactamente los mismos
        // bytes y el token seguir siendo válido. Manipular el contenido sí
        // invalida la firma siempre, que es lo que esta prueba quiere demostrar.
        String[] partes = token.split("\\.");
        String payloadAlterado = partes[1].substring(0, partes[1].length() - 1)
            + (partes[1].endsWith("X") ? "Y" : "X");
        String tokenAlterado = partes[0] + "." + payloadAlterado + "." + partes[2];

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

    /**
     * HU-AUT-007 / D-09 — el claim `amr` (RFC 8176) dice como se autentico la
     * sesion. Con la contrasena sola, `["pwd"]`: es lo que llevan todos los
     * tokens del login de siempre.
     */
    @Test
    void elLoginDeSiempreLlevaAmrPwd() {
        String token = jwtService.generarToken("cristianc", "JUGADOR", 0, UUID.randomUUID());

        Claims claims = jwtService.validarYObtenerClaims(token);

        assertEquals(java.util.List.of("pwd"), claims.get("amr", java.util.List.class));
        assertEquals(java.util.List.of("pwd"), JwtService.metodosDe(claims));
    }

    /** Tras el segundo factor, `["pwd","otp"]`: lo que busca ms-cumplimiento con AUDITORIA_EXIGIR_2FA. */
    @Test
    void conSegundoFactorLlevaAmrPwdOtp() {
        String token = jwtService.generarToken("cristianc", "ADMINISTRADOR", 2, UUID.randomUUID(),
            JwtService.AMR_CON_SEGUNDO_FACTOR);

        Claims claims = jwtService.validarYObtenerClaims(token);

        assertEquals(java.util.List.of("pwd", "otp"), claims.get("amr", java.util.List.class));
        assertEquals("ADMINISTRADOR", claims.get("rol", String.class));
        assertTrue(jwtService.esVersionVigente(claims, 2));
        assertTrue(JwtService.conSegundoFactor(JwtService.metodosDe(claims)));
    }

    /** Un token anterior a 2.2.0 no trae `amr`: equivale a «sin segundo factor». */
    @Test
    void sinAmrEquivaleASinSegundoFactor() {
        assertEquals(java.util.List.of(), JwtService.metodosDe(null));
        assertFalse(JwtService.conSegundoFactor(null));
        assertFalse(JwtService.conSegundoFactor(java.util.List.of("pwd")));
    }

    // ------------------------------------------- 7.3.9: sesiones administrativas

    @Test
    void unTokenAdministrativoCaducaALosQuinceMinutos() {
        ReflectionTestUtils.setField(jwtService, "minutosSesionAdministrativa", 15);

        for (String rol : List.of("MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR")) {
            Claims claims = jwtService.validarYObtenerClaims(
                jwtService.generarToken("ana", rol, 0, UUID.randomUUID(), List.of("pwd", "otp")));
            long segundos = claims.getExpiration().toInstant().getEpochSecond()
                - claims.getIssuedAt().toInstant().getEpochSecond();
            assertEquals(15 * 60, segundos, rol);
        }
    }

    @Test
    void unTokenDeJugadorSigueDurandoVeinticuatroHoras() {
        Claims claims = jwtService.validarYObtenerClaims(
            jwtService.generarToken("ana", "JUGADOR", 0, UUID.randomUUID()));
        long segundos = claims.getExpiration().toInstant().getEpochSecond()
            - claims.getIssuedAt().toInstant().getEpochSecond();
        assertEquals(24 * 3600, segundos);
    }

    @Test
    void elTokenLlevaElInstanteDeLaAutenticacionYLaRenovacionLoConserva() {
        Instant hace = Instant.now().minus(Duration.ofHours(2)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        String token = jwtService.generarToken("ana", "ADMINISTRADOR", 3, UUID.randomUUID(), List.of("pwd", "otp"),
            hace);
        Claims claims = jwtService.validarYObtenerClaims(token);

        assertEquals(hace, JwtService.autenticadoEn(claims));
        assertEquals(List.of("pwd", "otp"), JwtService.metodosDe(claims));
    }

    @Test
    void ningunaSesionAdministrativaPasaDeSuMaximo() {
        ReflectionTestUtils.setField(jwtService, "minutosSesionAdministrativa", 15);
        ReflectionTestUtils.setField(jwtService, "horasMaximasSesionAdministrativa", 12);
        Instant autenticado = Instant.parse("2026-10-13T00:00:00Z");

        // A 5 minutos del maximo, el token dura 5 minutos y no 15.
        Instant ahora = autenticado.plus(Duration.ofHours(12)).minus(Duration.ofMinutes(5));
        assertEquals(autenticado.plus(Duration.ofHours(12)),
            jwtService.expiracionPara("SUPER_ADMINISTRADOR", ahora, autenticado));
        // Lejos del maximo, los 15 minutos de inactividad.
        assertEquals(autenticado.plus(Duration.ofMinutes(75)),
            jwtService.expiracionPara("MODERADOR", autenticado.plus(Duration.ofHours(1)), autenticado));
        assertEquals(Duration.ofHours(12), jwtService.duracionMaximaDeSesion("ADMINISTRADOR"));
    }

    @Test
    void renovarNoAlargaLaSesionDeUnJugador() {
        Instant autenticado = Instant.parse("2026-10-13T00:00:00Z");
        Instant ahora = autenticado.plus(Duration.ofHours(20));

        assertEquals(autenticado.plus(Duration.ofHours(24)),
            jwtService.expiracionPara("JUGADOR", ahora, autenticado));
    }

    @Test
    void unTokenAnteriorSinAuthTimeSeTomaPorSuEmision() {
        Instant emitido = Instant.parse("2026-10-01T08:00:00Z");
        Claims anterior = org.mockito.Mockito.mock(Claims.class);
        org.mockito.Mockito.when(anterior.getIssuedAt()).thenReturn(java.util.Date.from(emitido));

        assertEquals(emitido, JwtService.autenticadoEn(anterior));
    }
}
