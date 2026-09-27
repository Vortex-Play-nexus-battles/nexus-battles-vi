package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.api;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.Remitente;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.security.Principal;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Quien escribe o lee, sacado del token y de ningun otro sitio — B6.
 *
 * <p>La misma lectura para las dos vias: el principal del CONNECT de STOMP y el
 * JWT de la peticion REST. Es lo que garantiza que «el remitente siempre es el
 * token» no dependa de por donde llegue el mensaje.
 *
 * <p><b>Solo personas.</b> Un token de servicio ({@code client_credentials},
 * rol SERVICIO) no escribe mensajes privados en nombre de nadie: hace falta uno
 * de los roles de persona de RF-RBAC-001. El {@code uid} sale del claim
 * {@code uid} (ADR-002), nunca del sujeto, que en ms-identidad es el apodo.
 */
public final class RemitenteDelToken {

    /** Roles de persona de RF-RBAC-001, tal como los deja {@code ConversorRolesJwt}. */
    static final Set<String> ROLES_DE_PERSONA = Set.of(
            "ROLE_JUGADOR", "ROLE_MODERADOR", "ROLE_ADMINISTRADOR", "ROLE_SUPER_ADMINISTRADOR");

    private RemitenteDelToken() {
    }

    /** Desde el principal de la sesion STOMP. */
    public static Remitente de(Principal principal) {
        if (!(principal instanceof JwtAuthenticationToken token)) {
            throw new AccessDeniedException("Los mensajes privados necesitan un jugador autenticado.");
        }
        return de(token.getToken(), token.getAuthorities());
    }

    /** Desde una autenticacion de la cadena HTTP. */
    public static Remitente de(Authentication autenticacion) {
        if (!(autenticacion instanceof JwtAuthenticationToken token)) {
            throw new AccessDeniedException("Los mensajes privados necesitan un jugador autenticado.");
        }
        return de(token.getToken(), token.getAuthorities());
    }

    static Remitente de(Jwt jwt, Collection<? extends GrantedAuthority> autoridades) {
        boolean esPersona = autoridades.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLES_DE_PERSONA::contains);
        if (!esPersona) {
            throw new AccessDeniedException("Solo una persona escribe y lee mensajes privados.");
        }
        UUID uid;
        try {
            uid = IdentidadDelToken.idDe(jwt);
        } catch (IllegalArgumentException sinIdentificador) {
            throw new AccessDeniedException("El token no trae el identificador del jugador.");
        }
        return new Remitente(uid, IdentidadDelToken.apodoDe(jwt));
    }
}
