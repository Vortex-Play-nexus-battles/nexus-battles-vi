package com.nexusbattles.plataforma.comentarios.imagenes;

import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;

/**
 * Quien pide una imagen, en lo unico que importa para decidir si la ve: si
 * es su autor y si modera — contrato 1.4.0 ({@code GET
 * /comentarios/imagenes/{imagenId}}).
 *
 * <p>La ruta es publica (una imagen de un comentario publicado se pinta con
 * un {@code <img>} normal, sin cabeceras), asi que puede llegar sin token. Si
 * llega con uno, se lee aqui: el autor ve las suyas aunque su comentario este
 * en revision, y MODERADOR, ADMINISTRADOR y SUPER_ADMINISTRADOR ven cualquiera,
 * porque moderar un comentario incluye mirar sus imagenes.
 *
 * <p>Un token de servicio no identifica a ninguna persona: cuenta como anonimo.
 *
 * @param uid el {@code uid} del token, o nulo sin sesion de usuario
 * @param modera si tiene un rol de moderacion
 */
public record Solicitante(String uid, boolean modera) {

    /** Sin token, o con uno que no es de usuario. */
    public static final Solicitante ANONIMO = new Solicitante(null, false);

    /** Los mismos roles que pueden entrar a la cola de moderacion (SecurityConfig). */
    static final Set<String> AUTORIDADES_DE_MODERACION =
            Set.of("ROLE_MODERADOR", "ROLE_ADMINISTRADOR", "ROLE_SUPER_ADMINISTRADOR");

    /** Lee la autenticacion que dejo la cadena de seguridad (nula si no hubo token). */
    public static Solicitante de(Authentication autenticacion) {
        if (!(autenticacion instanceof JwtAuthenticationToken jwt)) {
            return ANONIMO;
        }
        String uid;
        try {
            uid = IdentidadDelToken.idDe(jwt.getToken()).toString();
        } catch (IllegalArgumentException noEsDeUsuario) {
            uid = null;
        }
        boolean modera = jwt.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(AUTORIDADES_DE_MODERACION::contains);
        return new Solicitante(uid, modera);
    }

    /** Si puede ver una imagen que todavia no es publica. */
    public boolean puedeVerLaPrivadaDe(String autorId) {
        return modera || (uid != null && uid.equals(autorId));
    }
}
