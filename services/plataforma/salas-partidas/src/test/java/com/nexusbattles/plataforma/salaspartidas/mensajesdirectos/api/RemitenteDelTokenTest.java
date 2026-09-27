package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.api;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.Remitente;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("RemitenteDelToken · quien escribe sale del token y es una persona (B6)")
class RemitenteDelTokenTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static JwtAuthenticationToken token(String sub, UUID uid, String apodo, String rol) {
        Jwt.Builder jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(sub)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (uid != null) {
            jwt.claim("uid", uid.toString());
        }
        if (apodo != null) {
            jwt.claim("preferred_username", apodo);
        }
        return new JwtAuthenticationToken(jwt.build(), List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    @Test
    @DisplayName("el uid sale del claim uid y el apodo del token; el sub de ms-identidad es el apodo")
    void deUnTokenDeMsIdentidad() {
        Remitente remitente = RemitenteDelToken.de(token("ana", ANA, null, "JUGADOR"));
        assertAll(
                () -> assertEquals(ANA, remitente.id()),
                () -> assertEquals("ana", remitente.apodo()));
    }

    @Test
    @DisplayName("preferred_username manda sobre el sub para el apodo, y todos los roles de persona valen")
    void rolesDePersona() {
        for (String rol : List.of("JUGADOR", "MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR")) {
            assertEquals("Ana_B", RemitenteDelToken.de(token("x", ANA, "Ana_B", rol)).apodo(), rol);
        }
    }

    @Test
    @DisplayName("un servicio, un token sin uid ni sub UUID, o algo que no es un JWT: denegado")
    void denegados() {
        assertAll(
                () -> assertThrows(AccessDeniedException.class,
                        () -> RemitenteDelToken.de(token("salas-partidas", null, null, "SERVICIO"))),
                () -> assertThrows(AccessDeniedException.class,
                        () -> RemitenteDelToken.de(token("solo-apodo", null, null, "JUGADOR"))),
                () -> assertThrows(AccessDeniedException.class,
                        () -> RemitenteDelToken.de(new TestingAuthenticationToken("ana", "x", "ROLE_JUGADOR"))),
                () -> assertThrows(AccessDeniedException.class,
                        () -> RemitenteDelToken.de((java.security.Principal) () -> "ana")));
    }

    @Test
    @DisplayName("un token de Keycloak, con el uid en el sub, tambien vale")
    void subConUuid() {
        assertEquals(ANA, RemitenteDelToken.de(token(ANA.toString(), null, "ana", "JUGADOR")).id());
    }
}
