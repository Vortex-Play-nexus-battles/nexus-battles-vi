package com.nexusbattles.plataforma.salaspartidas.api;

/**
 * Cuerpo opcional de {@code POST /salas/{idSala}/participantes}, calcado del
 * esquema {@code IngresoRequest} del contrato.
 *
 * <p>Es opcional porque a una sala publica se entra sin nada: exigir un cuerpo
 * vacio para el caso normal seria pedirle al cliente que mande ruido. El
 * controlador lo recibe como {@code null} y pasa {@code null} como codigo, que
 * es lo que el dominio ya sabe rechazar en una sala privada.
 *
 * @param codigoInvitacion obligatorio unicamente si la sala es privada
 */
public record IngresoRequest(String codigoInvitacion) {

    /** El codigo de una peticion que puede no venir. Evita comprobar null dos veces. */
    static String codigoDe(IngresoRequest peticion) {
        return peticion == null ? null : peticion.codigoInvitacion();
    }
}
