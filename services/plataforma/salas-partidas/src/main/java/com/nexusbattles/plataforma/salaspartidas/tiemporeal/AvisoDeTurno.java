package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;

import java.util.UUID;

/**
 * Mensaje {@code partida.turno.cambiado} — RF-JUE-017.
 *
 * <p>Se publica cada vez que el turno pasa de manos. Va aparte del aviso de
 * accion resuelta porque el turno tambien cambia sin accion —abandono, tiempo
 * agotado— y la vista de combate tiene que enterarse igual.
 *
 * @param tipo        discriminador del canal
 * @param idPartida   partida afectada
 * @param idJugador   a quien le toca ahora
 * @param numeroTurno turno en curso, desde 1
 */
record AvisoDeTurno(String tipo, UUID idPartida, UUID idJugador, int numeroTurno) {

    static final String TIPO = "partida.turno.cambiado";

    static AvisoDeTurno de(Partida partida) {
        return new AvisoDeTurno(TIPO, partida.id(),
                partida.turnoActual().idJugador(), partida.turnoActual().numeroTurno());
    }
}
