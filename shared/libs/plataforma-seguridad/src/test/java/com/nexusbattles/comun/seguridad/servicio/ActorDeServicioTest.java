package com.nexusbattles.comun.seguridad.servicio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ActorDeServicio · el servicio que llama sale del claim azp, nunca del cuerpo")
class ActorDeServicioTest {

    private static Jwt token(Map<String, Object> claims) {
        return new Jwt("valor", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), claims);
    }

    @Test
    @DisplayName("un token de cuenta de servicio identifica al cliente por azp")
    void identificaAlServicio() {
        Jwt jwt = token(Map.of("azp", "ms-subastas", "sub", "service-account-ms-subastas",
                "realm_access", Map.of("roles", List.of("SERVICIO_SUBASTAS"))));

        assertThat(ActorDeServicio.desde(new JwtAuthenticationToken(jwt))).contains("ms-subastas");
    }

    @Test
    @DisplayName("sin azp no hay actor: el sub de un jugador no se confunde con un servicio")
    void sinAzpNoHayActor() {
        Jwt jwt = token(Map.of("sub", "11111111-1111-1111-1111-111111111111"));

        assertThat(ActorDeServicio.desde(new JwtAuthenticationToken(jwt))).isEmpty();
        assertThat(ActorDeServicio.desde((Jwt) null)).isEmpty();
    }

    @Test
    @DisplayName("una autenticacion que no es JWT tampoco es un servicio")
    void otraAutenticacionNoEsServicio() {
        assertThat(ActorDeServicio.desde(new UsernamePasswordAuthenticationToken("x", "y"))).isEmpty();
    }
}
