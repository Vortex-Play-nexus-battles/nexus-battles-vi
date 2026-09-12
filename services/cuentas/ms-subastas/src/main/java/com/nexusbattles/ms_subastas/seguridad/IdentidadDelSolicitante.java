package com.nexusbattles.ms_subastas.seguridad;

/**
 * Lo que se puede afirmar de quien hace la peticion despues de validar su
 * token. Nada mas: si un dato no viene firmado dentro del JWT, no aparece aqui.
 *
 * <p><b>Ojo con el apodo.</b> ms-identidad pone el apodo como sujeto del token,
 * no un identificador estable. El dominio de subastas trabaja con
 * {@code UUID jugadorId}, asi que un controlador no puede usar {@code apodo}
 * como si fuera el id del jugador: hace falta resolver esa traduccion antes, y
 * hoy no hay forma de hacerlo sin preguntarle a ms-identidad. Esta es la razon
 * por la que el validador no alcanza, por si solo, para desbloquear los
 * controladores REST.
 *
 * @param apodo        sujeto del token. Identifica a la persona, pero no es su UUID.
 * @param rol          rol con el que se emitio el token (JUGADOR, ADMINISTRADOR, ...).
 * @param versionToken version de rol vigente cuando se emitio. Se expone pero
 *                     <b>no se verifica</b> contra base de datos: ver README del
 *                     servicio, "Asunciones tomadas" punto 4.
 */
public record IdentidadDelSolicitante(String apodo, String rol, int versionToken) {
}
