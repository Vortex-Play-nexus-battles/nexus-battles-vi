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

    // ---- 7.4.9 (contracts/websocket/chatbot.yaml 1.0.0): quien abre el canal ----

    @Test
    @DisplayName("canal: un jugador entra con su uid como nombre, sin tocar las sesiones de visitante")
    void enElCanalUnJugadorEsSuUid() {
        UUID uid = UUID.randomUUID();

        assertThat(resolutor.enElCanal(token(Map.of("sub", "ana", "uid", uid.toString())), "visitante-0123456789"))
            .contains(new ResolutorDeIdentidad.EnElCanal(uid.toString(), true));
        verifyNoInteractions(sesiones);
    }

    @Test
    @DisplayName("canal: un visitante entra por la huella de su sesion, nunca por la sesion misma")
    void enElCanalUnVisitanteEsLaHuellaDeSuSesion() {
        String declarada = "visitante-0123456789abcdef";
        String emitida = "anon_" + "a".repeat(43);
        when(sesiones.validar(declarada)).thenReturn(Optional.empty());
        when(sesiones.validar(emitida)).thenReturn(Optional.of(mock(SesionAnonima.class)));

        // La declarada entra aunque no este registrada: se registra con su primer mensaje.
        assertThat(resolutor.enElCanal(null, declarada))
            .contains(new ResolutorDeIdentidad.EnElCanal("anonimo:" + SesionesAnonimas.huella(declarada), false));
        assertThat(resolutor.enElCanal(null, emitida)).hasValueSatisfying(quien -> {
            assertThat(quien.jugador()).isFalse();
            assertThat(quien.nombre()).isEqualTo("anonimo:" + SesionesAnonimas.huella(emitida))
                .doesNotContain(emitida);
        });
    }

    @Test
    @DisplayName("canal: sin token valido ni sesion vigente, o con un uid en la cabecera, no entra nadie")
    void enElCanalSinIdentidadNoEntraNadie() {
        UUID uid = UUID.randomUUID();
        String vencida = "anon_" + "b".repeat(43);
        when(sesiones.validar(vencida)).thenReturn(Optional.empty());
        when(sesiones.validar(uid.toString())).thenReturn(Optional.empty());
        when(sesiones.validar(null)).thenReturn(Optional.empty());

        assertThat(resolutor.enElCanal(null, vencida)).isEmpty();
        assertThat(resolutor.enElCanal(null, uid.toString())).isEmpty();
        assertThat(resolutor.enElCanal(token(Map.of("sub", uid.toString())), null)).isEmpty();
    }
}
