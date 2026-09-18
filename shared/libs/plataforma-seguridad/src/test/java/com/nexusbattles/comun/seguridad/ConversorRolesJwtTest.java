package com.nexusbattles.comun.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Movida desde {@code moderacion-sanciones} junto con la clase que prueba.
 * Ni una asercion cambio: si el comportamiento fuera distinto, esta prueba
 * lo diria.
 */
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

    // ---------------------------------------------------------------------
    // Tokens de ms-integridad: rol en singular e identificador en "uid".
    // Ver ADR-002; sin esto, la plataforma rechazaba el token que el propio
    // login de la aplicacion acababa de emitir.
    // ---------------------------------------------------------------------

    @Test
    void traduceElRolEnSingularQueEmiteMsIdentidad() {
        Jwt jwt = construirJwt(Map.of("rol", "JUGADOR"));

        AbstractAuthenticationToken resultado = conversor.convert(jwt);

        assertThat(resultado.getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_JUGADOR");
    }

    @Test
    void elIdentificadorEstableMandaSobreElSujeto() {
        // El "sub" de ms-identidad es el apodo, que es mutable: si fuera el
        // principal, cambiar de apodo convertiria a alguien en otra persona a
        // ojos de sus salas. El identificador estable viaja en "uid".
        Jwt jwt = construirJwt(Map.of(
                "sub", "cristianc",
                "uid", "11111111-2222-3333-4444-555555555555",
                "rol", "JUGADOR"));

        AbstractAuthenticationToken resultado = conversor.convert(jwt);

        assertThat(resultado.getName()).isEqualTo("11111111-2222-3333-4444-555555555555");
    }

    @Test
    void sinUidElPrincipalSigueSiendoElSujeto() {
        // Es el caso de Keycloak, donde el "sub" ya es el identificador
        // estable, y el de un usuario de ms-identidad anterior al campo.
        Jwt jwt = construirJwt(Map.of(
                "sub", "66666666-7777-8888-9999-000000000000",
                "realm_access", Map.of("roles", List.of("JUGADOR"))));

        AbstractAuthenticationToken resultado = conversor.convert(jwt);

        assertThat(resultado.getName()).isEqualTo("66666666-7777-8888-9999-000000000000");
    }

    @Test
    void realmAccessMandaSobreElRolEnSingularSiLlegaran_los_dos() {
        // Durante la migracion a Keycloak podrian coexistir; el formato del
        // emisor de destino es el que decide.
        Jwt jwt = construirJwt(Map.of(
                "rol", "JUGADOR",
                "realm_access", Map.of("roles", List.of("ADMINISTRADOR"))));

        AbstractAuthenticationToken resultado = conversor.convert(jwt);

        assertThat(resultado.getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_ADMINISTRADOR")
                .doesNotContain("ROLE_JUGADOR");
    }

    private Jwt construirJwt(Map<String, Object> claimsAdicionales) {
        Jwt.Builder constructor = Jwt.withTokenValue("token-de-prueba")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claims(claims -> claims.putAll(claimsAdicionales));
        if (!claimsAdicionales.containsKey("sub")) {
            constructor.subject("sujeto-de-prueba");
        }
        return constructor.build();
    }
}
