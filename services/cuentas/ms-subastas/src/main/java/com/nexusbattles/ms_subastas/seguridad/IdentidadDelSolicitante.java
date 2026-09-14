package com.nexusbattles.ms_subastas.seguridad;

import java.util.Optional;
import java.util.UUID;

/**
 * Lo que se puede afirmar de quien hace la peticion despues de validar su
 * token. Nada mas: si un dato no viene firmado dentro del JWT, no aparece aqui.
 *
 * @param apodo        sujeto del token. Identifica a la persona de cara a quien
 *                     lee, pero <b>es mutable</b>: se cambia desde el perfil y
 *                     desde la edicion de administracion. No usarlo como clave.
 * @param rol          rol con el que se emitio el token (JUGADOR, ADMINISTRADOR, ...).
 * @param versionToken version de rol vigente cuando se emitio. Se expone pero
 *                     <b>no se verifica</b> contra base de datos: ver README del
 *                     servicio, "Asunciones tomadas" punto 4.
 * @param jugadorId    identificador estable, del claim {@code uid}. Puede faltar
 *                     en tokens emitidos antes de que ms-identidad lo incluyera,
 *                     de ahi que sea opcional y no un UUID a secas.
 */
public record IdentidadDelSolicitante(String apodo, String rol, int versionToken, UUID jugadorId) {

    /**
     * El identificador estable, si el token lo trae. Para quien puede seguir
     * adelante sin el — el listado publico, por ejemplo, que no necesita saber
     * quien mira.
     */
    public Optional<UUID> jugadorIdOpcional() {
        return Optional.ofNullable(jugadorId);
    }

    /**
     * El identificador estable, o falla.
     *
     * <p><b>Obligatorio en toda operacion que mueva creditos:</b> pujar,
     * comprar de inmediato y publicar — publicar tambien, porque cobra comision
     * de publicacion (1 credito a 24 h, 3 a 48 h; ver
     * {@code CalculadorComisionPublicacion}).
     *
     * <p>Sin esto, una peticion con token valido pero sin {@code uid} podria
     * crear una subasta con vendedor nulo, o una puja que retiene creditos a
     * nombre de nadie. Fallar con 401 es la unica salida honesta: el token
     * acredita que alguien inicio sesion, pero no a quien cobrarle.
     *
     * @throws TokenInvalidoException si el token no trae el claim {@code uid}
     */
    public UUID exigirJugadorId() {
        if (jugadorId == null) {
            throw new TokenInvalidoException(
                    "el token no trae identificador de jugador: vuelve a iniciar sesion");
        }
        return jugadorId;
    }
}
