package com.nexusbattles.comun.seguridad;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduce el token de acceso a una autenticacion de Spring Security: de que
 * claim salen los roles y que identifica al usuario.
 *
 * <p>Vivia en {@code moderacion-sanciones}. Se movio aqui sin cambiar una linea
 * de su comportamiento cuando {@code salas-partidas} necesito lo mismo: el
 * segundo servicio que necesita una pieza es la senal de que la pieza es
 * compartida, no de que haya que copiarla.
 *
 * <h2>Dos formas de token, un solo conversor</h2>
 *
 * <p>La plataforma consume tokens de dos emisores posibles, y esta clase los
 * entiende a los dos sin que el servicio tenga que enterarse (ver
 * {@code docs/gobierno/ADR-002-identidad-de-usuario.md}):
 *
 * <ul>
 *   <li><b>ms-identidad</b> (el emisor de hoy): rol en el claim {@code rol},
 *       en singular, e identificador estable del usuario en {@code uid}. El
 *       {@code sub} es el <b>apodo</b>, que es mutable, porque el
 *       {@code SecurityInterceptor} del propio ms-identidad lo lee como
 *       identidad y cambiarlo romperia su cadena de administracion.</li>
 *   <li><b>Keycloak</b> (destino, RF-AUT / RF-RBAC): roles en
 *       {@code realm_access.roles} y {@code sub} ya con el identificador
 *       estable.</li>
 * </ul>
 *
 * <p><b>Por que el nombre del principal es {@code uid} cuando existe:</b> los
 * servicios de plataforma guardan al usuario por identificador estable
 * ({@code SalasController} hace {@code UUID.fromString} sobre el nombre del
 * principal, y el anfitrion de una sala se compara por ese valor). Con el
 * apodo como principal, un cambio de apodo convertiria a alguien en otra
 * persona a ojos de sus salas. Cuando el emisor pase a ser Keycloak, el
 * {@code sub} ya sera ese identificador y esta regla seguira valiendo sin
 * tocar nada.
 */
public class ConversorRolesJwt implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Identificador estable del usuario en los tokens de ms-identidad. */
    public static final String CLAIM_UID = "uid";

    /** Rol unico, en singular, en los tokens de ms-identidad. */
    public static final String CLAIM_ROL = "rol";

    /** Bloque de roles del realm, en los tokens de Keycloak. */
    public static final String CLAIM_REALM_ACCESS = "realm_access";

    private final JwtGrantedAuthoritiesConverter conversorPorDefecto = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        JwtAuthenticationConverter delegado = new JwtAuthenticationConverter();
        delegado.setJwtGrantedAuthoritiesConverter(this::extraerAuthorities);
        delegado.setPrincipalClaimName(jwt.hasClaim(CLAIM_UID) ? CLAIM_UID : "sub");
        return delegado.convert(jwt);
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extraerAuthorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim(CLAIM_REALM_ACCESS);
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
            return roles.stream()
                    .map(String.class::cast)
                    .map(ConversorRolesJwt::comoAuthority)
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
