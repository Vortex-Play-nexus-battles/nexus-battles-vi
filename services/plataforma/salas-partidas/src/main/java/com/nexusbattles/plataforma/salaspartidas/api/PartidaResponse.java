package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResultadoDePartida;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cuerpo de {@code GET /partidas/{idPartida}} — RF-JUE-017.
 *
 * <p>Forma exacta del esquema {@code Partida} de
 * {@code contracts/openapi/salas-partidas.yaml} 1.7.0.
 *
 * @param id                identificador de la partida
 * @param idSala            sala de la que salio
 * @param estado            EN_CURSO o FINALIZADA
 * @param participantes     en el orden de los turnos, sorteado al empezar (§6.1.3)
 * @param turnoActual       de quien es el turno y cual es
 * @param recompensaEnJuego creditos comprometidos en el combate
 * @param iniciadaEn        momento de arranque
 * @param resultado         GANADOR o EMPATE; nulo en curso (1.7.0)
 * @param ganadores         el heroe en pie o todo el equipo ganador (D-B7-15)
 * @param equipoGanador     solo por equipos
 * @param finalizadaEn      cuando termino, o nulo
 */
record PartidaResponse(
        UUID id,
        UUID idSala,
        EstadoPartida estado,
        List<ParticipanteResponse> participantes,
        TurnoResponse turnoActual,
        int recompensaEnJuego,
        Instant iniciadaEn,
        ResultadoDePartida resultado,
        List<UUID> ganadores,
        Integer equipoGanador,
        Instant finalizadaEn) {

    static PartidaResponse desde(Partida partida) {
        return desde(partida, Instant.now());
    }

    static PartidaResponse desde(Partida partida, Instant ahora) {
        return new PartidaResponse(
                partida.id(),
                partida.idSala(),
                partida.estado(),
                partida.participantes().stream().map(ParticipanteResponse::desde).toList(),
                new TurnoResponse(partida.turnoActual().idJugador(),
                        partida.turnoActual().numeroTurno(), segundosRestantes(partida, ahora)),
                partida.recompensaEnJuego(),
                partida.iniciadaEn(),
                partida.resultado().orElse(null),
                partida.ganadores().stream().map(ParticipanteDePartida::idJugador).toList(),
                partida.equipoGanador().orElse(null),
                partida.finalizadaEn());
    }

    /** Nulo sin tiempo por turno (D-B7-14); nunca negativo. */
    private static Integer segundosRestantes(Partida partida, Instant ahora) {
        if (partida.turnoVenceEn() == null) {
            return null;
        }
        return (int) Math.max(0, Duration.between(ahora, partida.turnoVenceEn()).toSeconds());
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
                    HeroeResponse.desde(participante.heroe(), participante.combate()),
                    participante.esIA(),
                    true,
                    participante.equipo(),
                    participante.creditosApostados());
        }
    }

    /**
     * Espejo de {@code HeroeEnPartida}. Nulo mientras no se conozca el heroe.
     * Desde 1.7.0 trae el estado de combate que calculo el motor: poder,
     * recargas, efectos y las acciones que puede jugar.
     */
    record HeroeResponse(String id, String nombre, String retratoUrl, Integer nivel,
                         int vidaActual, int vidaMaxima, String prototipo, Integer poderActual,
                         Integer poderMaximo, Map<String, Integer> recargas, List<EfectoResponse> efectosActivos,
                         List<EstadoDeCombate.AccionDisponible> acciones) {

        static HeroeResponse desde(HeroeDeCombate heroe) {
            return desde(heroe, null);
        }

        static HeroeResponse desde(HeroeDeCombate heroe, EstadoDeCombate combate) {
            if (heroe == null) {
                return null;
            }
            return new HeroeResponse(heroe.id(), heroe.nombre(), heroe.retratoUrl(),
                    heroe.nivel(), heroe.vidaActual(), heroe.vidaMaxima(), heroe.prototipo(),
                    combate == null ? null : combate.poderActual(),
                    combate == null ? null : combate.poderMaximo(),
                    combate == null ? Map.of() : combate.recargas(),
                    combate == null ? List.of() : combate.efectos().stream().map(EfectoResponse::desde).toList(),
                    combate == null ? List.of() : combate.acciones());
        }
    }

    /** Un efecto activo (esquema {@code HeroeEnPartida.efectosActivos}). */
    record EfectoResponse(String codigo, String nombre, Integer turnosRestantes, String tipo, Integer valor) {

        static EfectoResponse desde(EstadoDeCombate.Efecto efecto) {
            return new EfectoResponse(efecto.codigo(), efecto.nombre(),
                    efecto.hastaSuTurno() ? Integer.valueOf(1) : Integer.valueOf(efecto.turnos()),
                    efecto.tipo(), efecto.valor());
        }
    }

    /**
     * Turno en curso. {@code segundosRestantes} va nulo mientras el Product
     * Owner no fije el tiempo por turno (D-B7-14).
     */
    record TurnoResponse(UUID idJugador, int numeroTurno, Integer segundosRestantes) {
    }
}
