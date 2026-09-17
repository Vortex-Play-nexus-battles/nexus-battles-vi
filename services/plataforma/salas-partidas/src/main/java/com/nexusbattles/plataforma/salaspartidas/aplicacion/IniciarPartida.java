package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Arranque del combate — HU-SAL-004, RF-JUE-017.
 *
 * <p>Cuatro pasos: pedirle a la sala que empiece —ahi viven las reglas de quien
 * puede y cuando—, construir la partida con quien estaba dentro, guardar las
 * dos, y anunciarlo por el canal.
 *
 * <p><b>El orden no es casual.</b> Primero se guarda, despues se anuncia. Al
 * reves se anunciaria un combate que todavia podria no llegar a existir, y los
 * participantes saltarian a una vista de partida que el servidor no conoce.
 *
 * <p><b>Idempotencia.</b> Si la sala ya esta en juego, la propia sala rechaza el
 * segundo intento. Antes de eso se mira si ya hay partida para esa sala: si la
 * hay, se devuelve la que existe en vez de un error, porque pulsar dos veces
 * «empezar» no es un fallo del jugador y la respuesta correcta es la misma
 * partida.
 */
public class IniciarPartida {

    private final RepositorioDeSalas salas;
    private final RepositorioDePartidas partidas;
    private final CanalDePartida canal;
    private final Clock reloj;

    public IniciarPartida(RepositorioDeSalas salas, RepositorioDePartidas partidas,
                          CanalDePartida canal, Clock reloj) {
        this.salas = Objects.requireNonNull(salas);
        this.partidas = Objects.requireNonNull(partidas);
        this.canal = Objects.requireNonNull(canal);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /**
     * @param idSala        sala que arranca
     * @param idSolicitante quien lo pide; solo el anfitrion puede
     * @return la partida en curso
     * @throws SalaNoEncontrada si la sala no existe
     */
    public Partida ejecutar(UUID idSala, UUID idSolicitante) {
        Objects.requireNonNull(idSala, "Hace falta la sala que se quiere iniciar.");
        Objects.requireNonNull(idSolicitante, "Hace falta quien pide iniciarla.");

        Sala sala = salas.buscarPorId(idSala).orElseThrow(() -> new SalaNoEncontrada(idSala));

        // Pulsar dos veces no es un error: se devuelve la partida que ya existe.
        var yaIniciada = partidas.buscarPorSala(idSala);
        if (yaIniciada.isPresent()) {
            return yaIniciada.get();
        }

        sala.iniciarPartida(idSolicitante);

        Partida partida = partidas.guardar(Partida.iniciar(sala, reloj.instant()));
        salas.guardar(sala);

        canal.anunciarInicio(partida);

        return partida;
    }
}
