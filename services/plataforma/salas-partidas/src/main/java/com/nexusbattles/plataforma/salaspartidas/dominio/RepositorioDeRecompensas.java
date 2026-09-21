package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Almacen de recompensas por jugar — puerto de salida, HU-JUE-012, CA-05.
 *
 * <p>Gemelo de {@link RepositorioDeLiquidaciones}: el dominio declara que
 * necesita recordar que recompensas quedan por acreditar, y la
 * infraestructura elige donde.
 */
public interface RepositorioDeRecompensas {

    /** Guarda o actualiza la recompensa de una partida (una por partida). */
    RecompensaDePartida guardar(RecompensaDePartida recompensa);

    /** La recompensa de una partida, si alguna vez se intento. */
    Optional<RecompensaDePartida> buscarPorPartida(UUID idPartida);

    /**
     * Las que siguen {@link RecompensaDePartida.Estado#PENDIENTE}, de la mas
     * antigua a la mas nueva, hasta {@code maximo}.
     */
    List<RecompensaDePartida> pendientes(int maximo);
}
