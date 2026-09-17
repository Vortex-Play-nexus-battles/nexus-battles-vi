package com.nexusbattles.plataforma.salaspartidas.dominio;

/**
 * Estado del combate — RF-JUE-017.
 *
 * <p>Son exactamente los dos valores del enumerado {@code estado} de
 * {@code Partida} en {@code contracts/openapi/salas-partidas.yaml}. Una partida
 * existe porque alguien la inicio: no hay estado previo al arranque, y por eso
 * no hay {@code PENDIENTE}. Lo que hay antes es una <b>sala</b>, que tiene su
 * propio ciclo de vida en {@link EstadoSala}.
 */
public enum EstadoPartida {

    /** El combate esta vivo y hay un turno en curso. */
    EN_CURSO,

    /** El combate termino. El turno deja de avanzar. */
    FINALIZADA
}
