package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta;
import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.NoEsTuTurno;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaYaTerminada;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResolucionDelMotor;
import com.nexusbattles.plataforma.salaspartidas.dominio.SinObjetivoPosible;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * El jugador juega su turno y el combate avanza — RF-JUE-006, RF-JUE-017.
 *
 * <p>Sustituye a {@code AvanzarTurno}, que solo rotaba. El orden es el que pide
 * el contrato del canal, y cada paso depende del anterior:
 *
 * <ol>
 *   <li>Comprobar que puede jugar: partida viva y turno suyo.</li>
 *   <li>Elegir objetivo.</li>
 *   <li>Pedir al <b>motor de combate</b> cuanto dano hace el golpe.</li>
 *   <li>Aplicarlo a la vida que esta partida persiste.</li>
 *   <li>Anunciar {@code partida.accion.resuelta} para que se muevan las barras.</li>
 *   <li>Terminar si solo queda uno, o pasar el turno.</li>
 * </ol>
 *
 * <p><b>Guardar antes de anunciar</b>, como en el resto del servicio. Al reves
 * se anunciaria una vida que todavia podria perderse, y las barras quedarian
 * mostrando un numero que la base no tiene.
 *
 * <p><b>Lo que este servicio NO decide:</b> cuanto dano hace un golpe. Eso lo
 * resuelve el motor, que es su dueno; aqui solo se aplica el resultado a la
 * vida y se anuncia.
 */
public class EjecutarAccion {

    /** Codigo por defecto cuando el cliente no manda uno. El motor no lo usa todavia. */
    static final String ACCION_BASICA = "ATAQUE_BASICO";

    private final RepositorioDePartidas partidas;
    private final CanalDePartida canal;
    private final MotorDeCombate motor;

    public EjecutarAccion(RepositorioDePartidas partidas, CanalDePartida canal,
                          MotorDeCombate motor) {
        this.partidas = Objects.requireNonNull(partidas);
        this.canal = Objects.requireNonNull(canal);
        this.motor = Objects.requireNonNull(motor, "Sin motor no hay combate.");
    }

    /**
     * @param idPartida  partida en la que se juega
     * @param idJugador  jugador autenticado que manda la accion
     * @param idObjetivo a quien apunta; opcional si solo hay un rival en pie
     * @param codigo     accion elegida; nulo cae en el ataque basico
     * @return la partida despues del golpe
     * @throws PartidaNoEncontrada si no existe
     * @throws PartidaYaTerminada  si el combate ya acabo
     * @throws NoEsTuTurno         si no le toca a quien envia
     * @throws SinObjetivoPosible  si no hay a quien apuntar, o el elegido no vale
     */
    public Partida ejecutar(UUID idPartida, UUID idJugador, UUID idObjetivo, String codigo) {
        Objects.requireNonNull(idPartida, "Hace falta la partida en la que se juega.");
        Objects.requireNonNull(idJugador, "Hace falta quien juega el turno.");

        Partida partida = partidas.buscarPorId(idPartida)
                .orElseThrow(() -> new PartidaNoEncontrada(idPartida));

        // El orden importa: una partida terminada se rechaza como terminada, no
        // como turno ajeno. Al ultimo en jugar, un «no es tu turno» no le
        // explicaria nada, porque su turno si era.
        if (partida.estado() == EstadoPartida.FINALIZADA) {
            throw new PartidaYaTerminada(idPartida);
        }
        if (!partida.turnoActual().idJugador().equals(idJugador)) {
            throw new NoEsTuTurno();
        }

        ParticipanteDePartida atacante = participante(partida, idJugador);
        ParticipanteDePartida objetivo = elegirObjetivo(partida, idJugador, idObjetivo);

        if (atacante.heroe() == null || objetivo.heroe() == null) {
            // Sin heroe no hay estadisticas que mandar al motor. Pasa con los
            // participantes anteriores a la puerta de SCRUM-1074 y con la IA,
            // cuyo heroe decide el motor. Se pasa turno sin golpear en vez de
            // inventar un ataque.
            return soloPasarTurno(partida);
        }

        ResolucionDelMotor resolucion = motor.resolver(atacante.heroe(), objetivo.heroe());

        ParticipanteDePartida golpeado =
                partida.aplicarDano(objetivo.idJugador(), resolucion.danoAplicado());

        boolean termino = partida.terminarSiSoloQuedaUno();
        if (!termino) {
            partida.avanzarTurno();
        }

        Partida guardada = partidas.guardar(partida);

        canal.anunciarAccionResuelta(new AccionResuelta(
                guardada.id(), idJugador,
                new AccionResuelta.Accion(
                        codigo == null || codigo.isBlank() ? ACCION_BASICA : codigo,
                        resolucion.categoria(), null),
                List.of(new AccionResuelta.Afectado(
                        golpeado.idJugador(),
                        golpeado.heroe().vidaActual(),
                        golpeado.heroe().vidaMaxima(),
                        -resolucion.danoAplicado()))));

        // El turno se anuncia DESPUES de la accion: quien mira la vista ve
        // primero moverse la barra y luego de quien es el turno, que es el
        // orden en el que ocurren las cosas.
        if (!termino) {
            canal.anunciarTurno(guardada);
        }

        return guardada;
    }

    /**
     * Turno perdido sin golpe.
     *
     * <p>No se anuncia accion resuelta porque no se resolvio ninguna: anunciar
     * un golpe de cero dano moveria las barras a lo tonto y ensuciaria el
     * historial del combate.
     */
    private Partida soloPasarTurno(Partida partida) {
        partida.avanzarTurno();
        Partida guardada = partidas.guardar(partida);
        canal.anunciarTurno(guardada);
        return guardada;
    }

    private static ParticipanteDePartida participante(Partida partida, UUID id) {
        return partida.participantes().stream()
                .filter(p -> p.idJugador().equals(id))
                .findFirst()
                .orElseThrow(() -> new SinObjetivoPosible("Ese jugador no esta en la partida."));
    }

    /**
     * A quien golpea.
     *
     * <p>Con un solo rival en pie se resuelve solo: en un 1v1 no hay ambiguedad
     * y pedir el identificador seria burocracia. Con dos o mas, elegir por el
     * jugador seria decidir su jugada, asi que se exige.
     */
    private static ParticipanteDePartida elegirObjetivo(Partida partida, UUID atacante,
                                                        UUID idObjetivo) {
        List<ParticipanteDePartida> rivales = partida.enPie().stream()
                .filter(p -> !p.idJugador().equals(atacante))
                .toList();

        if (rivales.isEmpty()) {
            throw new SinObjetivoPosible("No queda nadie en pie a quien atacar.");
        }
        if (idObjetivo == null) {
            if (rivales.size() > 1) {
                throw new SinObjetivoPosible(
                        "Hay mas de un rival en pie: indica a quien atacas.");
            }
            return rivales.get(0);
        }
        return rivales.stream()
                .filter(p -> p.idJugador().equals(idObjetivo))
                .findFirst()
                .orElseThrow(() -> new SinObjetivoPosible(
                        "Ese objetivo no esta en la partida o ya cayo."));
    }
}
