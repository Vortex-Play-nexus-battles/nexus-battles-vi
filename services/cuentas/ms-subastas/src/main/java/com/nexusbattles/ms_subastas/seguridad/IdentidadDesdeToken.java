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
 * en ms-identidad todavia (HU-SUB-010 #553). B8 lo confirmo contra el
 * documento y contra RBAC: 7.7.4 describe un «usuario especial» de
 * UPB-COMPANY sin decir como se reconoce, y la Tabla 24 / {@code rbac.yaml}
 * solo tienen JUGADOR, MODERADOR, ADMINISTRADOR y SUPER_ADMINISTRADOR.
 * Identificarlo por un uid configurado o por un rol nuevo seria inventarlo;
 * el dia que ms-identidad lo emita en el token, se lee aqui y todo lo demas
 * (exento de comision y del tope de publicaciones, marcado «Oficial», primero
 * en el listado) ya lo respeta.
 *
 * <p>El apodo (claim {@code sub}) viaja solo para mostrar quien pujo, anonimizado.
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
        return new Identidad(jugadorId, false, jwt.getToken().getSubject());
    }
}
