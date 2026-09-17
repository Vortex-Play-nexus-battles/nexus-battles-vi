package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;

import java.util.List;
import java.util.UUID;

/**
 * Mensaje {@code sala.partida.iniciada} — HU-SAL-004, RF-JUE-017.
 *
 * <p>Espejo del mensaje {@code PartidaIniciada} de
 * {@code contracts/websocket/salas-partidas.yaml}. El nombre del tipo y los
 * campos salen del contrato, que se escribio antes (regla 1); esta clase no
 * inventa ninguno.
 *
 * <p>El orden de turnos es una salida que el PDF exige explicitamente, y es lo
 * unico que la sala de espera necesita para pasar al combate. El roster
 * completo —{@code participantes} del contrato, con heroe y vida— no viaja
 * aqui porque este servicio no lo conoce: el heroe equipado lo tiene el
 * inventario y la verificacion de HU-SAL-003 todavia no es obligatoria en el
 * ingreso. Omitir el campo dice la verdad; rellenarlo con una vida inventada
 * pintaria barras falsas. Mientras tanto la vista lo resuelve con
 * {@code GET /partidas/{id}}.
 *
 * @param tipo          discriminador del canal, como en el resto de avisos
 * @param idSala        sala de la que sale el combate
 * @param idPartida     partida que acaba de empezar
 * @param ordenDeTurnos jugadores en el orden en que jugaran
 * @param turnoActual   quien abre el combate
 */
record AvisoDeInicioDePartida(String tipo, UUID idSala, UUID idPartida,
                              List<UUID> ordenDeTurnos, Turno turnoActual) {

    static final String TIPO = "sala.partida.iniciada";

    static AvisoDeInicioDePartida de(Partida partida) {
        return new AvisoDeInicioDePartida(TIPO, partida.idSala(), partida.id(),
                partida.participantes().stream().map(ParticipanteDePartida::idJugador).toList(),
                new Turno(partida.turnoActual().idJugador(), partida.turnoActual().numeroTurno()));
    }

    /** Turno tal como viaja en el cable. Misma forma que {@code turnoCambiado}. */
    record Turno(UUID idJugador, int numeroTurno) {
    }
}
