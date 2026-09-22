package com.nexusbattles.comun.seguridad.servicio;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/**
 * Lado servidor del patron: quien es el servicio que llama — ADR-001.
 *
 * <p>Keycloak identifica al cliente que pidio el token en el claim {@code azp}
 * (authorized party, OIDC Core §2). Es lo que un servidor de recursos usa para
 * la bitacora y para reglas del tipo «solo subastas puede liberar un bloqueo de
 * subasta». La autorizacion gruesa (¿es un servicio autorizado?) sigue siendo
 * por rol, con {@code hasRole(...)} en la cadena de seguridad de cada servicio.
 *
 * <p>Lo que este tipo <b>no</b> hace, y ningun servicio debe hacer: deducir de
 * aqui al jugador afectado. El propietario de un recurso llega en la peticion
 * como dato de negocio ({@code propietarioUid}); el token solo dice que servicio
 * habla.
 */
public final class ActorDeServicio {

    /** Claim de OIDC con el {@code client_id} que obtuvo el token. */
    public static final String CLAIM = "azp";

    private ActorDeServicio() {
        // Utilidad: no se instancia.
    }

    /** {@code client_id} del servicio que llama, si la autenticacion es un JWT con {@code azp}. */
    public static Optional<String> desde(Authentication autenticacion) {
        if (autenticacion instanceof JwtAuthenticationToken jwt) {
            return desde(jwt.getToken());
        }
        return Optional.empty();
    }

    public static Optional<String> desde(Jwt token) {
        if (token == null) {
            return Optional.empty();
        }
        String actor = token.getClaimAsString(CLAIM);
        return actor == null || actor.isBlank() ? Optional.empty() : Optional.of(actor);
    }
}
