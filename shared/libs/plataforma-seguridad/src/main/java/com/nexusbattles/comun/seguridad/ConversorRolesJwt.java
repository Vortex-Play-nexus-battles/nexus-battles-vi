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
 * Traduce el claim "realm_access.roles" que emite Keycloak (RF-AUT / RF-RBAC)
 * a authorities de Spring Security con prefijo ROLE_, para poder usar
 * hasRole()/hasAnyRole() en la configuracion de seguridad.
 *
 * <p>Vivia en {@code moderacion-sanciones}. Se movio aqui sin cambiar una linea
 * de su comportamiento cuando {@code salas-partidas} necesito lo mismo: el
 * segundo servicio que necesita una pieza es la senal de que la pieza es
 * compartida, no de que haya que copiarla.
 */
public class ConversorRolesJwt implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter conversorPorDefecto = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        JwtAuthenticationConverter delegado = new JwtAuthenticationConverter();
        delegado.setJwtGrantedAuthoritiesConverter(this::extraerAuthorities);
        return delegado.convert(jwt);
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extraerAuthorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
            return conversorPorDefecto.convert(jwt);
        }

        return roles.stream()
                .map(String.class::cast)
                .map(rol -> new SimpleGrantedAuthority("ROLE_" + rol))
                .collect(Collectors.toList());
    }
}
