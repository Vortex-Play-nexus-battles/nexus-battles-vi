package com.nexusbattles.plataforma.salaspartidas.seguridad;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Las dos caras de la identidad, leidas del token en un solo sitio — ADR-002.
 *
 * <p><b>Por que existe.</b> Esta lectura estaba escrita dos veces: una en
 * {@code SalasController} y otra en {@code ChatController}. La primera se
 * corrigio en el PR #404 —{@code UUID.fromString(sub)} reventaba con un 500
 * porque tras ADR-002 el sujeto de {@code ms-identidad} es el <i>apodo</i>, no
 * un UUID— y la segunda se quedo con el fallo. Copiar la regla es copiar el
 * error; aqui vive una vez.
 *
 * <p>El identificador estable manda dentro del servicio y viaja en {@code uid}.
 * El apodo solo hace falta para hablar con el inventario, que hoy reconoce al
 * jugador por su nombre visible.
 */
public final class IdentidadDelToken {

    /** Claim del identificador estable que emite ms-identidad (ADR-002). */
    public static final String CLAIM_UID = "uid";

    private IdentidadDelToken() {
    }

    /**
     * Identificador estable del jugador.
     *
     * <p>Se prefiere {@code uid}; el sujeto queda de respaldo para los tokens
     * anteriores a ADR-002, donde el sujeto <i>era</i> el identificador.
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
     * Nombre visible.
     *
     * <p>Se busca en {@code preferred_username} —el estandar de OIDC y el que
     * emite Keycloak— y si no esta, en {@code apodo}. Como ultimo recurso queda
     * el sujeto: en los tokens anteriores a ADR-002 el sujeto era el apodo.
     */
    public static String apodoDe(Jwt token) {
        return primerTextoNoVacio(
                token.getClaimAsString("preferred_username"),
                token.getClaimAsString("apodo"),
                token.getSubject());
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
