package com.nexusbattles.plataforma.moderacionsanciones.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConversorRolesJwtTest {

    private final ConversorRolesJwt conversor = new ConversorRolesJwt();

    @Test
    void traduceLosRolesDeRealmAccessAAuthoritiesConPrefijoRole() {
        Jwt jwt = construirJwt(Map.of("realm_access", Map.of("roles", List.of("ADMINISTRADOR", "MODERADOR"))));

        AbstractAuthenticationToken resultado = conversor.convert(jwt);

        // Spring Security agrega ademas una autoridad FACTOR_BEARER propia (rastreo
        // del factor de autenticacion); solo nos interesa que los roles si esten.
        assertThat(resultado.getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_ADMINISTRADOR", "ROLE_MODERADOR");
    }

    @Test
    void noAgregaRolesCuandoElTokenNoTraeRealmAccess() {
        Jwt jwt = construirJwt(Map.of());

        AbstractAuthenticationToken resultado = conversor.convert(jwt);

        assertThat(resultado.getAuthorities())
                .extracting(Object::toString)
                .noneMatch(autoridad -> autoridad.startsWith("ROLE_"));
    }

    private Jwt construirJwt(Map<String, Object> claimsAdicionales) {
        return Jwt.withTokenValue("token-de-prueba")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claims(claims -> claims.putAll(claimsAdicionales))
                .build();
    }
}
