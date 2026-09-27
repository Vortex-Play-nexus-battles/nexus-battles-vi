package com.nexusbattles.plataforma.salaspartidas.dominio;

/**
 * El final formal de una partida — salas-partidas.yaml 1.7.0, §6.1.3.
 */
public enum ResultadoDePartida {
    /** Queda en pie un solo heroe, o un solo equipo en el modo cooperativo. */
    GANADOR,
    /** No queda nadie en pie: un reflejo o un sangrado pueden tumbar a los dos a la vez. */
    EMPATE
}
