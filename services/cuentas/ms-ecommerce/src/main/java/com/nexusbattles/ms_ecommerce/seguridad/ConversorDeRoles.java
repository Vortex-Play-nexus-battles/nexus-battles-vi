package com.nexusbattles.ms_ecommerce.seguridad;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Traduce el token de acceso a una autenticacion de Spring Security.
 *
 * <p>Reproduce {@code ConversorRolesJwt} de shared/libs/plataforma-seguridad,
 * regla por regla, porque este modulo sigue en Maven y no puede enlazar esa
 * biblioteca (ver settings.gradle). Si aquella cambia, esta tiene que
 * cambiar igual; la prueba {@code SeguridadDelCarritoTest} afirma las dos
 * formas de token para que la divergencia se note.
 *
 * <ul>
 *   <li><b>ms-identidad</b> (ADR-002): rol en {@code rol}, identificador
 *       estable en {@code uid}, sujeto = apodo (mutable). El nombre del
 *       principal es {@code uid} cuando existe.</li>
 *   <li><b>Keycloak</b> (destino): roles en {@code realm_access.roles} y
 *       sujeto ya estable.</li>
 * </ul>
 */
public class ConversorDeRoles implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String CLAIM_UID = "uid";
    public static final String CLAIM_ROL = "rol";
    public static final String CLAIM_REALM_ACCESS = "realm_access";

    /** Las autoridades de los roles de usuario (SeguridadConfig.ROLES_DE_USUARIO con su prefijo). */
    private static final Set<String> ROLES_DE_USUARIO = Set.of(
            "ROLE_JUGADOR", "ROLE_MODERADOR", "ROLE_ADMINISTRADOR", "ROLE_SUPER_ADMINISTRADOR");

    private final JwtGrantedAuthoritiesConverter conversorPorDefecto = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        JwtAuthenticationConverter delegado = new JwtAuthenticationConverter();
        delegado.setJwtGrantedAuthoritiesConverter(this::extraerAuthorities);
        delegado.setPrincipalClaimName(jwt.hasClaim(CLAIM_UID) ? CLAIM_UID : "sub");
        return delegado.convert(jwt);
    }

    /** Identificador estable del usuario: {@code uid}, y el sujeto de respaldo. */
    public static String identificadorDe(Jwt jwt) {
        String uid = jwt.getClaimAsString(CLAIM_UID);
        return uid != null && !uid.isBlank() ? uid : jwt.getSubject();
    }

    /**
     * B5 — el jugador de una peticion a una ruta publica (la vitrina): su
     * identificador si trae el token de un USUARIO; vacio si no trae token o si
     * es de un servicio, que no tiene lista de deseos ni inventario propio.
     */
    public static Optional<String> usuarioDe(Authentication autenticacion) {
        if (!(autenticacion instanceof JwtAuthenticationToken token) || !autenticacion.isAuthenticated()) {
            return Optional.empty();
        }
        boolean esUsuario = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLES_DE_USUARIO::contains);
        return esUsuario ? Optional.ofNullable(identificadorDe(token.getToken())) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extraerAuthorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim(CLAIM_REALM_ACCESS);
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
            return roles.stream()
                    .map(String.class::cast)
                    .map(ConversorDeRoles::comoAuthority)
                    .collect(Collectors.toList());
        }
        String rol = jwt.getClaimAsString(CLAIM_ROL);
        if (rol != null && !rol.isBlank()) {
            return List.of(comoAuthority(rol));
        }
        return conversorPorDefecto.convert(jwt);
    }

    private static GrantedAuthority comoAuthority(String rol) {
        return new SimpleGrantedAuthority("ROLE_" + rol);
    }
}
