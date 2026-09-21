package com.nexusbattles.plataforma.notificaciones.seguridad;

import com.nexusbattles.comun.seguridad.CadenaDeSeguridad;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Set;

/**
 * Seguridad del servicio de notificaciones (HU-NOT-006).
 *
 * <p>El andamiaje —sin CSRF, sin estado y token traducido a autenticacion—
 * viene de {@link CadenaDeSeguridad}, compartido con el resto de la
 * plataforma (ADR-002). Aqui solo las reglas de rutas de este dominio.
 *
 * <p><b>La bandeja es de su dueno.</b> {@code /api/v1/users/{usuarioId}/**}
 * solo la atiende el usuario cuyo {@code uid} coincide con el de la ruta.
 * Antes el identificador de la ruta se aceptaba sin mas, asi que cualquiera
 * leia y marcaba la bandeja de cualquiera con solo escribir su id.
 *
 * <p><b>Emitir es cosa de servicios.</b> {@code /api/v1/internal/**} lo
 * llaman ms-identidad y ms-subastas con su credencial de servicio
 * (ADR-001/ADR-005): {@code ROLE_SERVICIO}. Un usuario no emite avisos.
 *
 * <p><b>El handshake del canal esta abierto</b> porque el navegador no puede
 * poner cabeceras ahi; el JWT se exige en el CONNECT de STOMP
 * ({@code AutenticacionStomp}). Actuator queda abierto para la sonda de salud
 * (regla 3).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Los roles con bandeja: personas, no servicios. */
    static final Set<String> ROLES_DE_USUARIO =
            Set.of("ROLE_JUGADOR", "ROLE_MODERADOR", "ROLE_ADMINISTRADOR", "ROLE_SUPER_ADMINISTRADOR");

    @Bean
    public ConversorRolesJwt conversorRolesJwt() {
        return new ConversorRolesJwt();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ConversorRolesJwt conversor) throws Exception {
        CadenaDeSeguridad.aplicarBase(http, conversor);

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/api/v1/internal/**").hasRole("SERVICIO")
                .requestMatchers("/api/v1/users/{usuarioId}/**").access(soloElDuenoDeLaBandeja())
                .anyRequest().authenticated());

        return http.build();
    }

    /**
     * Autoriza si el {@code uid} del token es el {@code usuarioId} de la ruta
     * y el token es de un usuario. Un token de servicio no tiene bandeja; un
     * token de otro usuario no ve la ajena. En ambos casos 403, no 404: no se
     * revela si la bandeja existe.
     */
    static AuthorizationManager<RequestAuthorizationContext> soloElDuenoDeLaBandeja() {
        return (autenticacion, contexto) -> {
            Authentication auth = autenticacion.get();
            if (!(auth instanceof JwtAuthenticationToken jwt) || !esUsuario(jwt)) {
                return new AuthorizationDecision(false);
            }
            String delToken;
            try {
                delToken = IdentidadDelToken.idDe(jwt.getToken()).toString();
            } catch (IllegalArgumentException tokenSinIdentificador) {
                return new AuthorizationDecision(false);
            }
            String deLaRuta = contexto.getVariables().get("usuarioId");
            return new AuthorizationDecision(delToken.equalsIgnoreCase(deLaRuta));
        };
    }

    private static boolean esUsuario(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(autoridad -> ROLES_DE_USUARIO.contains(autoridad.getAuthority()));
    }
}
