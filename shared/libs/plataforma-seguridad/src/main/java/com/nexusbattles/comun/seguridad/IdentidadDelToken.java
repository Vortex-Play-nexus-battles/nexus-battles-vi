package com.nexusbattles.comun.seguridad;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

/**
 * Las dos caras de la identidad de un usuario, leidas del token en un solo
 * sitio — ADR-002.
 *
 * <p><b>Por que vive aqui.</b> Nacio en {@code salas-partidas} porque esa
 * lectura estaba escrita dos veces ({@code SalasController} y
 * {@code ChatController}) y la segunda copia conservo el fallo que la primera
 * ya habia corregido ({@code UUID.fromString(sub)} reventaba porque tras
 * ADR-002 el sujeto es el <i>apodo</i>). Cuando {@code comentarios} y
 * {@code notificaciones} necesitaron la misma lectura, se movio a la
 * biblioteca compartida: el segundo servicio que necesita una pieza es la
 * senal de que la pieza es comun, no de que haya que copiarla.
 *
 * <p>El identificador estable manda dentro de cada servicio y viaja en
 * {@code uid}. El apodo solo hace falta para hablar con quien todavia
 * reconoce al jugador por su nombre visible (inventario) y para mostrarlo.
 */
public final class IdentidadDelToken {

    /** Claim del identificador estable que emite ms-identidad (ADR-002). */
    public static final String CLAIM_UID = ConversorRolesJwt.CLAIM_UID;

    private IdentidadDelToken() {
    }

    /**
     * Identificador estable del usuario.
     *
     * <p>Se prefiere {@code uid}; el sujeto queda de respaldo para los tokens
     * anteriores a ADR-002, donde el sujeto <i>era</i> el identificador, y
     * para Keycloak, donde el sujeto ya es el identificador estable.
     *
     * @throws IllegalArgumentException si ninguno de los dos es un UUID. Se deja
     *                                  salir a proposito: un token asi no se
     *                                  puede atender y taparlo daria un 200 con
     *                                  la identidad equivocada.
     */
    public static UUID idDe(Jwt token) {
        String uid = token.getClaimAsString(CLAIM_UID);
        return UUID.fromString(uid != null && !uid.isBlank() ? uid : token.getSubject());
    }

    /**
     * Identificador estable a partir de la autenticacion que dejo la cadena
     * de seguridad (o {@code AutenticacionStomp} en el canal).
     *
     * @throws IllegalArgumentException si la autenticacion no es un JWT o el
     *                                  token no identifica a un usuario
     */
    public static UUID idDe(Authentication autenticacion) {
        if (autenticacion instanceof JwtAuthenticationToken jwt) {
            return idDe(jwt.getToken());
        }
        throw new IllegalArgumentException("La autenticacion no lleva un token de usuario.");
    }

    /**
     * Nombre visible.
     *
     * <p>Se busca en {@code preferred_username} —el estandar de OIDC y el que
     * emite Keycloak— y si no esta, en {@code apodo}. Como ultimo recurso queda
     * el sujeto: en los tokens de ms-identidad el sujeto es el apodo.
     */
    public static String apodoDe(Jwt token) {
        return primerTextoNoVacio(
                token.getClaimAsString("preferred_username"),
                token.getClaimAsString("apodo"),
                token.getSubject());
    }

    /** Nombre visible a partir de la autenticacion; ver {@link #apodoDe(Jwt)}. */
    public static String apodoDe(Authentication autenticacion) {
        if (autenticacion instanceof JwtAuthenticationToken jwt) {
            return apodoDe(jwt.getToken());
        }
        throw new IllegalArgumentException("La autenticacion no lleva un token de usuario.");
    }

    private static String primerTextoNoVacio(String... candidatos) {
        for (String candidato : candidatos) {
            if (candidato != null && !candidato.isBlank()) {
                return candidato;
            }
        }
        return null;
    }
}
