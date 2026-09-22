package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.NoEsTuTurno;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaYaTerminada;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;

import java.util.Objects;
import java.util.UUID;

/**
 * El turno pasa de manos — RF-JUE-017.
 *
 * <p>Tres comprobaciones y un efecto. Las tres comprobaciones son las que el
 * contrato del canal exige antes de aceptar nada: que la partida exista, que
 * siga viva, y que quien manda sea de quien es el turno. El efecto es rotar y
 * anunciarlo.
 *
 * <p><b>Quien envia sale del token, nunca del mensaje.</b> Si el identificador
 * viajara en el cuerpo, cualquiera podria jugar el turno de otro con solo
 * escribir su UUID: la partida entera se podria conducir desde una sola sesion.
 *
 * <p><b>Lo que este caso de uso NO hace: resolver la accion.</b> El dano, los
 * efectos y quien gana son del motor de combate, que el Project Charter excluye
 * de este bloque. Aqui solo vive «a quien le toca ahora», que es lo unico que
 * este servicio puede afirmar. Cuando el motor publique su contrato, se
 * intercalara antes de rotar: resolver, anunciar {@code partida.accion.resuelta}
 * y entonces pasar el turno.
 */
public class AvanzarTurno {

    private final RepositorioDePartidas partidas;
    private final CanalDePartida canal;

    public AvanzarTurno(RepositorioDePartidas partidas, CanalDePartida canal) {
        this.partidas = Objects.requireNonNull(partidas);
        this.canal = Objects.requireNonNull(canal);
    }

    /**
     * @param idPartida partida en la que se juega
     * @param idJugador jugador autenticado que manda la accion
     * @return la partida con el turno ya rotado
     * @throws PartidaNoEncontrada si la partida no existe
     * @throws PartidaYaTerminada  si el combate ya acabo
     * @throws NoEsTuTurno         si no le toca a quien envia
     */
    public Partida ejecutar(UUID idPartida, UUID idJugador) {
        Objects.requireNonNull(idPartida, "Hace falta la partida en la que se juega.");
        Objects.requireNonNull(idJugador, "Hace falta quien juega el turno.");

        Partida partida = partidas.buscarPorId(idPartida)
                .orElseThrow(() -> new PartidaNoEncontrada(idPartida));

        // El orden importa: una partida terminada se rechaza como terminada,
        // no como «no es tu turno». Al reves, el ultimo en jugar recibiria un
        // mensaje que no explica nada —su turno era, y aun asi se le rechaza—.
        if (partida.estado() == EstadoPartida.FINALIZADA) {
            throw new PartidaYaTerminada(idPartida);
        }
        if (!partida.turnoActual().idJugador().equals(idJugador)) {
            throw new NoEsTuTurno();
        }

        partida.avanzarTurno();

        // Guardar antes de anunciar, como en el resto del servicio: al reves se
        // anunciaria un turno que todavia podria perderse, y las vistas
        // quedarian esperando a alguien a quien el servidor no le toca.
        Partida guardada = partidas.guardar(partida);
        canal.anunciarTurno(guardada);

        return guardada;
    }
}
