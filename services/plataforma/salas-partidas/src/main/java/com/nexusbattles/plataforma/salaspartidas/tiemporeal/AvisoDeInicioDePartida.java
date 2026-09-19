package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;

import java.util.List;
import java.util.UUID;

/**
 * Mensaje {@code sala.partida.iniciada} — HU-SAL-004, HU-SAL-005, RF-JUE-017.
 *
 * <p>Espejo del mensaje {@code PartidaIniciada} de
 * {@code contracts/websocket/salas-partidas.yaml}. El nombre del tipo y los
 * campos salen del contrato, que se escribio antes (regla 1); esta clase no
 * inventa ninguno.
 *
 * <p><b>Desde P2.4 lleva el roster.</b> Antes se omitia `participantes` porque
 * este servicio no conocia el heroe de nadie. Ahora la puerta de heroe
 * (SCRUM-1074) lo guarda al entrar, asi que el aviso puede traer la ficha
 * completa y la vista de batalla pinta la barra de vida real (RF-JUE-009) sin
 * una segunda peticion.
 *
 * <p>El apodo sale de la <b>sala</b> y no de la partida: la partida solo conoce
 * identificadores, y quien guardo el apodo al dejar entrar fue la sala.
 *
 * @param tipo          discriminador del canal, como en el resto de avisos
 * @param idSala        sala de la que sale el combate
 * @param idPartida     partida que acaba de empezar
 * @param ordenDeTurnos jugadores en el orden en que jugaran
 * @param turnoActual   quien abre el combate
 * @param participantes roster con heroe y vida inicial
 */
record AvisoDeInicioDePartida(String tipo, UUID idSala, UUID idPartida,
                              List<UUID> ordenDeTurnos, Turno turnoActual,
                              List<Participante> participantes) {

    static final String TIPO = "sala.partida.iniciada";

    static AvisoDeInicioDePartida de(Sala sala, Partida partida) {
        return new AvisoDeInicioDePartida(TIPO, partida.idSala(), partida.id(),
                partida.participantes().stream().map(ParticipanteDePartida::idJugador).toList(),
                new Turno(partida.turnoActual().idJugador(), partida.turnoActual().numeroTurno()),
                partida.participantes().stream().map(p -> Participante.de(sala, p)).toList());
    }

    /** Turno tal como viaja en el cable. Misma forma que {@code turnoCambiado}. */
    record Turno(UUID idJugador, int numeroTurno) {
    }

    /** Espejo del esquema {@code Participante} del AsyncAPI. */
    record Participante(ResumenJugador jugador, Heroe heroe, boolean esIA, Integer equipo) {

        static Participante de(Sala sala, ParticipanteDePartida enCombate) {
            UUID id = enCombate.idJugador();
            FichaDeParticipante ficha = sala.fichaDe(id);
            return new Participante(
                    new ResumenJugador(id, apodoDe(ficha, enCombate)),
                    Heroe.de(enCombate.heroe()),
                    enCombate.esIA(),
                    enCombate.equipo());
        }

        /**
         * El heroe de la IA no esta en la sala y no tiene apodo de cuentas: se
         * le da uno fijo para que la vista pueda etiquetar su barra. El contrato
         * exige {@code apodo} y dejarlo vacio obligaria a la vista a inventarse
         * el texto.
         */
        private static String apodoDe(FichaDeParticipante ficha, ParticipanteDePartida enCombate) {
            if (ficha != null) {
                return ficha.apodo();
            }
            return enCombate.esIA() ? "Heroe de la IA" : "Jugador";
        }
    }

    /** Espejo de {@code ResumenJugador}. Sin avatar ni rango: no son de aqui. */
    record ResumenJugador(UUID id, String apodo) {
    }

    /**
     * Heroe en el cable. Nulo cuando no se conoce: participante de una sala
     * anterior a la migracion V7. Se dice con null en vez de inventar una
     * vida, porque una barra a 100/100 falsa es peor que una que no se pinta.
     *
     * <p>Desde HU-SAL-004 la IA si trae heroe —el del anfitrion a plena vida—,
     * asi que ya no es un caso sin heroe.
     */
    record Heroe(String id, String nombre, String retratoUrl, Integer nivel,
                 int vidaActual, int vidaMaxima) {

        static Heroe de(HeroeDeCombate heroe) {
            return heroe == null ? null : new Heroe(heroe.id(), heroe.nombre(),
                    heroe.retratoUrl(), heroe.nivel(), heroe.vidaActual(), heroe.vidaMaxima());
        }
    }
}
