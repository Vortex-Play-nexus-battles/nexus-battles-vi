package com.nexusbattles.plataforma.moderacionsanciones.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JerarquiaDeRoles · Tabla 24: el Super Administrador puede todo lo del Administrador")
class JerarquiaDeRolesTest {

    private static TestingAuthenticationToken con(String... roles) {
        TestingAuthenticationToken autenticacion = new TestingAuthenticationToken("quien", null, roles);
        autenticacion.setAuthenticated(true);
        return autenticacion;
    }

    @Test
    @DisplayName("SUPER_ADMINISTRADOR alcanza ADMINISTRADOR y MODERADOR; ADMINISTRADOR alcanza MODERADOR")
    void jerarquia() {
        assertThat(JerarquiaDeRoles.rolesDe(con("ROLE_SUPER_ADMINISTRADOR")))
                .containsExactlyInAnyOrder("SUPER_ADMINISTRADOR", "ADMINISTRADOR", "MODERADOR");
        assertThat(JerarquiaDeRoles.rolesDe(con("ROLE_ADMINISTRADOR")))
                .containsExactlyInAnyOrder("ADMINISTRADOR", "MODERADOR");
        assertThat(JerarquiaDeRoles.rolesDe(con("ROLE_MODERADOR"))).containsExactly("MODERADOR");
        assertThat(JerarquiaDeRoles.alcanza(con("ROLE_JUGADOR"), JerarquiaDeRoles.MODERADOR)).isFalse();
    }

    @Test
    @DisplayName("un servicio no es un moderador, pero ve el detalle de la lista negra")
    void servicio() {
        assertThat(JerarquiaDeRoles.alcanza(con("ROLE_SERVICIO"), JerarquiaDeRoles.MODERADOR)).isFalse();
        assertThat(JerarquiaDeRoles.puedeVerDetalleDeListaNegra(con("ROLE_SERVICIO"))).isTrue();
        assertThat(JerarquiaDeRoles.puedeVerDetalleDeListaNegra(con("ROLE_SUPER_ADMINISTRADOR"))).isTrue();
        assertThat(JerarquiaDeRoles.puedeVerDetalleDeListaNegra(con("ROLE_JUGADOR"))).isFalse();
    }

    @Test
    @DisplayName("anonimo, nulo o sin autenticar: ningun rol, ningun detalle")
    void anonimo() {
        AnonymousAuthenticationToken anonimo = new AnonymousAuthenticationToken("clave", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        TestingAuthenticationToken sinAutenticar = new TestingAuthenticationToken("x", null, "ROLE_MODERADOR");
        sinAutenticar.setAuthenticated(false);

        assertThat(JerarquiaDeRoles.rolesDe(anonimo)).isEmpty();
        assertThat(JerarquiaDeRoles.rolesDe(null)).isEmpty();
        assertThat(JerarquiaDeRoles.rolesDe(sinAutenticar)).isEmpty();
        assertThat(JerarquiaDeRoles.puedeVerDetalleDeListaNegra(anonimo)).isFalse();
    }

    @Test
    @DisplayName("el cuerpo del 401/403 escapa la ruta: una comilla no rompe el JSON")
    void escapeDeLaRuta() {
        assertThat(SecurityConfig.json("/api/v1/\"x\\y")).isEqualTo("/api/v1/\\\"x\\\\y");
        assertThat(SecurityConfig.json("a\nb")).isEqualTo("a\\u000ab");
        assertThat(SecurityConfig.json(null)).isEmpty();
    }
}
