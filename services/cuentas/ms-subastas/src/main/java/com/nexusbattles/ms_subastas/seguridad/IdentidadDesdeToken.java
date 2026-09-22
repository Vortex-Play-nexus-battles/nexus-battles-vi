package com.nexusbattles.ms_subastas.seguridad;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import com.nexusbattles.ms_subastas.subastas.port.IdentidadClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Identidad del solicitante desde el token ya validado por la cadena de
 * seguridad (ADR-002). El {@code uid} es el identificador estable del
 * jugador; sin el no se opera, porque una subasta o una puja tienen que
 * quedar a nombre de alguien que exista en ms-identidad.
 *
 * <p>No valida nada por su cuenta: la firma, la caducidad y el emisor los
 * comprobo el servidor de recursos antes de llegar aqui. Si aun asi no hay
 * autenticacion en el contexto (una ruta publica que llamara a esto por
 * error), se rechaza como no autenticado, no se inventa un jugador.
 *
 * <p>{@code esMaestroDeJuego} sigue en {@code false}: no hay rol para eso
 * en ms-identidad todavia (HU-SUB-010 #553).
 */
@Component
public class IdentidadDesdeToken implements IdentidadClient {

    @Override
    public Identidad actual() {
        return desde(SecurityContextHolder.getContext().getAuthentication());
    }

    /** Visible para las pruebas: la misma traduccion sin pasar por el contexto. */
    Identidad desde(Authentication autenticacion) {
        if (!(autenticacion instanceof JwtAuthenticationToken jwt)) {
            throw new TokenInvalidoException("falta un token de usuario valido");
        }
        final UUID jugadorId;
        try {
            jugadorId = IdentidadDelToken.idDe(jwt.getToken());
        } catch (IllegalArgumentException malFormado) {
            throw new TokenInvalidoException("el identificador de jugador del token no es valido", malFormado);
        }
        return new Identidad(jugadorId, false);
    }
}
