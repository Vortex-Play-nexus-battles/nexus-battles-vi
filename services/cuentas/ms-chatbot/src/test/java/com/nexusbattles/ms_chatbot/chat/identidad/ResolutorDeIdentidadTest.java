package com.nexusbattles.ms_chatbot.chat.identidad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("1.4.0 · la identidad de un jugador es solo el claim uid (ADR-002)")
class ResolutorDeIdentidadTest {

    private final SesionesAnonimas sesiones = mock(SesionesAnonimas.class);
    private final ResolutorDeIdentidad resolutor = new ResolutorDeIdentidad(sesiones);

    private static JwtAuthenticationToken token(Map<String, Object> claims) {
        Jwt.Builder jwt = Jwt.withTokenValue("token-crudo").header("alg", "RS256")
            .issuedAt(Instant.parse("2026-10-14T10:00:00Z")).expiresAt(Instant.parse("2026-10-14T11:00:00Z"));
        claims.forEach(jwt::claim);
        return new JwtAuthenticationToken(jwt.build());
    }

    @Test
    void conUidEsEseJugadorAunqueLaCabeceraDigaOtraCosa() {
        UUID uid = UUID.randomUUID();
        Optional<IdentidadDelChat> quien = resolutor.resolver(
            token(Map.of("sub", "ana", "uid", uid.toString(), "rol", "JUGADOR")), "visitante-0123456789");

        assertThat(quien).hasValueSatisfying(identidad -> {
            assertThat(identidad.autenticado()).isTrue();
            assertThat(identidad.uid()).isEqualTo(uid.toString());
            assertThat(identidad.claveDeConversacion()).isEqualTo(uid.toString());
            assertThat(identidad.tokenCrudo()).isEqualTo("token-crudo");
        });
        verifyNoInteractions(sesiones);
    }

    // El caso que cierra 1.4.0: un token sin uid cuyo `sub` (el apodo) tiene
    // forma de UUID. Antes, IdentidadDelToken lo tomaba como uid: un apodo
    // elegido igual al uid de otro jugador abria su conversacion.
    @Test
    void sinUidNuncaSeUsaElSubAunqueTengaFormaDeUuid() {
        UUID ajeno = UUID.randomUUID();
        when(sesiones.validar(null)).thenReturn(Optional.empty());

        Optional<IdentidadDelChat> quien = resolutor.resolver(token(Map.of("sub", ajeno.toString())), null);

        assertThat(quien).isEmpty();
    }

    @Test
    void unUidSinFormaDeUuidNoIdentificaANadie() {
        when(sesiones.validar(null)).thenReturn(Optional.empty());

        assertThat(resolutor.resolver(token(Map.of("sub", "ana", "uid", "no-es-un-uuid")), null)).isEmpty();
        assertThat(resolutor.resolver(token(Map.of("sub", "ana", "uid", "  ")), null)).isEmpty();
        assertThat(resolutor.resolver(token(Map.of("sub", "ana", "uid", 42)), null)).isEmpty();
    }
}
