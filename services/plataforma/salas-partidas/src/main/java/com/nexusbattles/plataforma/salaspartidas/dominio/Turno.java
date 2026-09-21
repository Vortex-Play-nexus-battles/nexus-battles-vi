package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.Objects;
import java.util.UUID;

/**
 * Turno en curso de una partida — RF-JUE-017.
 *
 * <p>Espejo del objeto {@code turnoActual} del esquema {@code Partida}. Lleva de
 * quien es el turno y cual es su numero, que empieza en 1 y solo sube.
 *
 * <p><b>{@code segundosRestantes} no esta aqui a proposito.</b> El contrato lo
 * declara anulable porque el limite de tiempo por turno lo impone el motor de
 * combate, que esta fuera de este bloque. Cuando exista, entra sin romper nada:
 * es un campo mas en la respuesta, no una regla de este dominio.
 *
 * @param idJugador   a quien le toca actuar
 * @param numeroTurno turno en curso, desde 1
 */
public record Turno(UUID idJugador, int numeroTurno) {

    public Turno {
        Objects.requireNonNull(idJugador, "Un turno es siempre de alguien.");
        if (numeroTurno < 1) {
            throw new IllegalArgumentException("El primer turno es el 1: no hay turno cero.");
        }
    }

    /** Primer turno de la partida. */
    public static Turno primero(UUID idJugador) {
        return new Turno(idJugador, 1);
    }

    /** El siguiente turno, para quien actue a continuacion. */
    public Turno siguiente(UUID idJugador) {
        return new Turno(idJugador, numeroTurno + 1);
    }
}
