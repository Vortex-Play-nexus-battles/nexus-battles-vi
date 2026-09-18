package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de {@code GET /partidas/{idPartida}} — RF-JUE-017.
 *
 * <p>Forma exacta del esquema {@code Partida} de
 * {@code contracts/openapi/salas-partidas.yaml}.
 *
 * @param id                identificador de la partida
 * @param idSala            sala de la que salio
 * @param estado            EN_CURSO o FINALIZADA
 * @param participantes     en el orden de los turnos
 * @param turnoActual       de quien es el turno y cual es
 * @param recompensaEnJuego creditos comprometidos en el combate
 * @param iniciadaEn        momento de arranque
 */
record PartidaResponse(
        UUID id,
        UUID idSala,
        EstadoPartida estado,
        List<ParticipanteResponse> participantes,
        TurnoResponse turnoActual,
        int recompensaEnJuego,
        Instant iniciadaEn) {

    static PartidaResponse desde(Partida partida) {
        return new PartidaResponse(
                partida.id(),
                partida.idSala(),
                partida.estado(),
                partida.participantes().stream().map(ParticipanteResponse::desde).toList(),
                new TurnoResponse(partida.turnoActual().idJugador(),
                        partida.turnoActual().numeroTurno(), null),
                partida.recompensaEnJuego(),
                partida.iniciadaEn());
    }

    /**
     * Participante del combate.
     *
     * <p>{@code jugador} viaja como identificador y no como ficha completa: el
     * apodo y el avatar pertenecen al modulo de cuentas, y este servicio no los
     * guarda ni los inventa.
     *
     * <p>{@code listo} es siempre verdadero mientras no exista la fase de
     * preparacion: quien esta en una partida ya iniciada, esta listo por
     * definicion.
     */
    record ParticipanteResponse(
            UUID jugador,
            HeroeResponse heroe,
            boolean esIA,
            boolean listo,
            Integer equipo,
            int creditosApostados) {

        static ParticipanteResponse desde(ParticipanteDePartida participante) {
            return new ParticipanteResponse(
                    participante.idJugador(),
                    HeroeResponse.desde(participante.heroe()),
                    participante.esIA(),
                    true,
                    participante.equipo(),
                    participante.creditosApostados());
        }
    }

    /** Espejo de {@code HeroeEnPartida}. Nulo mientras no se conozca el heroe. */
    record HeroeResponse(String id, String nombre, String retratoUrl, Integer nivel,
                         int vidaActual, int vidaMaxima) {

        static HeroeResponse desde(HeroeDeCombate heroe) {
            if (heroe == null) {
                return null;
            }
            return new HeroeResponse(heroe.id(), heroe.nombre(), heroe.retratoUrl(),
                    heroe.nivel(), heroe.vidaActual(), heroe.vidaMaxima());
        }
    }

    /**
     * Turno en curso.
     *
     * <p>{@code segundosRestantes} va nulo: el limite de tiempo lo impone el
     * motor de combate, fuera de este bloque. El contrato lo declara anulable
     * justamente por eso.
     */
    record TurnoResponse(UUID idJugador, int numeroTurno, Integer segundosRestantes) {
    }
}
