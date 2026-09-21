package com.nexusbattles.ms_cumplimiento.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El conversor tiene que entender las dos formas de token que circulan
 * (ADR-002) y, si no reconoce ninguna, caer al comportamiento por defecto
 * de Spring en vez de inventar roles.
 */
class ConversorDeRolesTest {

    private final ConversorDeRoles conversor = new ConversorDeRoles();

    @Test
    @DisplayName("Token de ms-identidad: el principal es el uid y el rol sale de `rol`")
    void tokenDeIdentidad() {
        UUID uid = UUID.randomUUID();
        Jwt jwt = base("ana").claim("uid", uid.toString()).claim("rol", "SUPER_ADMINISTRADOR").build();

        AbstractAuthenticationToken auth = conversor.convert(jwt);

        assertThat(auth.getName()).isEqualTo(uid.toString());
        assertThat(roles(auth)).containsExactly("ROLE_SUPER_ADMINISTRADOR");
        assertThat(ConversorDeRoles.identificadorDe(jwt)).isEqualTo(uid.toString());
    }

    @Test
    @DisplayName("Token de Keycloak: roles de realm_access y el sujeto como principal")
    void tokenDeKeycloak() {
        UUID sujeto = UUID.randomUUID();
        Jwt jwt = base(sujeto.toString())
                .claim("preferred_username", "ana")
                .claim("realm_access", Map.of("roles", List.of("ADMINISTRADOR", "JUGADOR")))
                .build();

        AbstractAuthenticationToken auth = conversor.convert(jwt);

        assertThat(auth.getName()).isEqualTo(sujeto.toString());
        assertThat(roles(auth)).containsExactlyInAnyOrder("ROLE_ADMINISTRADOR", "ROLE_JUGADOR");
        assertThat(ConversorDeRoles.identificadorDe(jwt)).isEqualTo(sujeto.toString());
    }

    @Test
    @DisplayName("realm_access sin lista de roles no cuenta como Keycloak: se sigue mirando `rol`")
    void realmAccessMalformado() {
        Jwt jwt = base("svc").claim("realm_access", Map.of("roles", "no-es-lista")).claim("rol", "SERVICIO").build();

        assertThat(roles(conversor.convert(jwt))).containsExactly("ROLE_SERVICIO");
    }

    @Test
    @DisplayName("Sin `rol` ni realm_access se cae al conversor por defecto (scope -> SCOPE_)")
    void sinRolesConocidos() {
        Jwt jwt = base("otro").claim("scope", "leer").build();

        assertThat(roles(conversor.convert(jwt))).containsExactly("SCOPE_leer");
    }

    @Test
    @DisplayName("`rol` en blanco tampoco es un rol")
    void rolEnBlanco() {
        Jwt jwt = base("otro").claim("rol", "  ").build();

        assertThat(roles(conversor.convert(jwt))).doesNotContain("ROLE_  ");
        assertThat(ConversorDeRoles.identificadorDe(base("otro").claim("uid", " ").build())).isEqualTo("otro");
    }

    private static Jwt.Builder base(String sujeto) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject(sujeto);
    }

    /**
     * Solo los roles y scopes: Spring Security 7 anade por su cuenta la
     * autoridad {@code FACTOR_BEARER} a toda autenticacion por token, y esa no
     * la decide el conversor.
     */
    private static List<String> roles(AbstractAuthenticationToken auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_") || a.startsWith("SCOPE_"))
                .toList();
    }
}
