package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
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
 */
public class ObtenerPartida {

    private final RepositorioDePartidas partidas;

    public ObtenerPartida(RepositorioDePartidas partidas) {
        this.partidas = Objects.requireNonNull(partidas);
    }

    /**
     * @throws PartidaNoEncontrada si no existe
     */
    public Partida ejecutar(UUID idPartida) {
        Objects.requireNonNull(idPartida, "Hace falta la partida que se quiere consultar.");
        return partidas.buscarPorId(idPartida)
                .orElseThrow(() -> new PartidaNoEncontrada(idPartida));
    }
}
