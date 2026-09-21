package com.nexusbattles.ms_subastas.seguridad;

import com.nexusbattles.ms_subastas.subastas.port.IdentidadClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * La identidad sale del token que la cadena de seguridad ya valido (ADR-002):
 * aqui solo se traduce. Lo que la cadena rechaza (firma, caducidad, emisor)
 * se prueba en {@link SeguridadWebConfigTest}.
 */
class IdentidadDesdeTokenTest {

    private final IdentidadDesdeToken identidad = new IdentidadDesdeToken();

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static JwtAuthenticationToken tokenCon(Object uid) {
        var jwt = Jwt.withTokenValue("validado").header("alg", "RS256").subject("lyra")
                .claim("rol", "JUGADOR").claim("ver", 1)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        if (uid != null) {
            jwt.claim("uid", uid);
        }
        return new JwtAuthenticationToken(jwt.build(), List.of(new SimpleGrantedAuthority("ROLE_JUGADOR")));
    }

    @Test
    void resuelveElJugadorDesdeElClaimUid() {
        UUID uid = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(tokenCon(uid.toString()));

        IdentidadClient.Identidad actual = identidad.actual();

        assertEquals(uid, actual.usuarioId());
    }

    @Test
    void noPuedeSaberSiEsMaestroDeJuegoYAsumeQueNo() {
        SecurityContextHolder.getContext().setAuthentication(tokenCon(UUID.randomUUID().toString()));
        assertFalse(identidad.actual().esMaestroDeJuego());
    }

    @Test
    void unTokenValidoPeroSinUidSeRechaza() {
        // Sin uid, el sujeto es el apodo y no identifica a un jugador de ms-identidad.
        SecurityContextHolder.getContext().setAuthentication(tokenCon(null));
        assertThrows(TokenInvalidoException.class, identidad::actual);
    }

    @Test
    void unUidQueNoEsUuidSeRechaza() {
        SecurityContextHolder.getContext().setAuthentication(tokenCon("no-es-uuid"));
        assertThrows(TokenInvalidoException.class, identidad::actual);
    }

    @Test
    void sinAutenticacionEnElContextoSeRechaza() {
        SecurityContextHolder.clearContext();
        assertThrows(TokenInvalidoException.class, identidad::actual);
    }

    @Test
    void unaAutenticacionQueNoEsUnTokenSeRechaza() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alguien", "clave", List.of()));
        assertThrows(TokenInvalidoException.class, identidad::actual);
    }
}
