package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaAjena;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;

import java.util.Objects;
import java.util.UUID;

/**
 * Estado del combate — RF-JUE-017.
 *
 * <p>Se consulta al entrar a la vista de batalla y al reconectar tras una caida
 * del canal. Durante el juego normal el avance llega por WebSocket: esta ruta
 * es para pintar la primera vez y para ponerse al dia, no para ir preguntando.
 *
 * <p><b>Desde 1.7.0 (B7) solo la ven sus participantes</b> y los roles de
 * operacion: el poder, las cargas y las acciones de cada heroe son de la
 * partida, no publicos. Quien pregunta sale del token, nunca de la ruta.
 */
public class ObtenerPartida {

    private final RepositorioDePartidas partidas;

    public ObtenerPartida(RepositorioDePartidas partidas) {
        this.partidas = Objects.requireNonNull(partidas);
    }

    /**
     * Sin control de quien pregunta: para los usos internos que ya lo hicieron.
     *
     * @throws PartidaNoEncontrada si no existe
     */
    public Partida ejecutar(UUID idPartida) {
        Objects.requireNonNull(idPartida, "Hace falta la partida que se quiere consultar.");
        return partidas.buscarPorId(idPartida)
                .orElseThrow(() -> new PartidaNoEncontrada(idPartida));
    }

    /**
     * @param quien        jugador autenticado que pregunta
     * @param deOperacion  si tiene un rol de operacion (moderador, administrador)
     * @throws PartidaNoEncontrada si no existe
     * @throws PartidaAjena        si no la juega y no es de operacion
     */
    public Partida ejecutar(UUID idPartida, UUID quien, boolean deOperacion) {
        Partida partida = ejecutar(idPartida);
        if (!deOperacion && !partida.esParticipante(quien)) {
            throw new PartidaAjena();
        }
        return partida;
    }
}
