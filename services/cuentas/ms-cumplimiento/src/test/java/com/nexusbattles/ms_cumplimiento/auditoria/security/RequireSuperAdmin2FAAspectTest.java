package com.nexusbattles.ms_cumplimiento.auditoria.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La guarda con el segundo factor ENCENDIDO. Con el apagado (por defecto) ya
 * la ejercita {@code AuditLogControllerTest} a traves de la cadena real;
 * aqui se prueba la rama que hoy ningun entorno activa, para que el dia que
 * el PO la encienda ya se sepa que hace.
 */
class RequireSuperAdmin2FAAspectTest {

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Con exigir-2fa=true")
    class ConSegundoFactorExigido {

        private final RequireSuperAdmin2FAAspect aspecto = new RequireSuperAdmin2FAAspect(true);

        @Test
        @DisplayName("Un superadministrador cuyo token declara mfa en amr pasa")
        void conAmrPasa() {
            autenticar(jwtDeSuperAdmin(List.of("pwd", "mfa")));

            assertThatCode(aspecto::verificar).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Un superadministrador sin segundo factor en el token es rechazado")
        void sinAmrRechaza() {
            autenticar(jwtDeSuperAdmin(List.of("pwd")));

            assertThatThrownBy(aspecto::verificar)
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("2FA");
        }

        @Test
        @DisplayName("Sin claim amr tampoco pasa")
        void sinClaimRechaza() {
            autenticar(jwtDeSuperAdmin(null));

            assertThatThrownBy(aspecto::verificar).isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Una autenticacion que no es JWT puede declarar el segundo factor en sus detalles")
        void detallesConSegundoFactor() {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    "root", null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMINISTRADOR")));
            auth.setDetails((RequireSuperAdmin2FAAspect.TwoFactorAwareDetails) () -> true);
            autenticar(auth);

            assertThatCode(aspecto::verificar).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Una autenticacion sin JWT ni detalles de segundo factor es rechazada")
        void sinDetallesRechaza() {
            autenticar(new UsernamePasswordAuthenticationToken(
                    "root", null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMINISTRADOR"))));

            assertThatThrownBy(aspecto::verificar).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("Con cualquier configuracion")
    class Siempre {

        private final RequireSuperAdmin2FAAspect aspecto = new RequireSuperAdmin2FAAspect(false);

        @Test
        @DisplayName("Sin autenticacion en el contexto se niega")
        void sinAutenticacion() {
            assertThatThrownBy(aspecto::verificar)
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("no autenticada");
        }

        @Test
        @DisplayName("Un administrador corriente no es superadministrador")
        void rolInsuficiente() {
            autenticar(new UsernamePasswordAuthenticationToken(
                    "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))));

            assertThatThrownBy(aspecto::verificar)
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Super Administrador");
        }
    }

    private static void autenticar(Authentication auth) {
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static JwtAuthenticationToken jwtDeSuperAdmin(List<String> amr) {
        Jwt.Builder jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("root")
                .claim("rol", "SUPER_ADMINISTRADOR");
        if (amr != null) {
            jwt.claim("amr", amr);
        }
        return new JwtAuthenticationToken(jwt.build(),
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMINISTRADOR")));
    }
}
