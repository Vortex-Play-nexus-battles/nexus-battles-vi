package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.Optional;
import java.util.UUID;

/**
 * Almacen de partidas — puerto de salida.
 *
 * <p>Mismo criterio que {@link RepositorioDeSalas}: el dominio declara que
 * necesita guardar y recuperar partidas, y la infraestructura elige la base.
 */
public interface RepositorioDePartidas {

    /** Guarda la partida y devuelve el estado asentado. */
    Partida guardar(Partida partida);

    /** Busca por identificador. Vacio si no existe. */
    Optional<Partida> buscarPorId(UUID id);

    /**
     * Busca la partida de una sala.
     *
     * <p>Una sala tiene como mucho una partida: es lo que impide iniciarla dos
     * veces, y la unicidad esta tambien en la base de datos para que no dependa
     * solo de esta comprobacion.
     */
    Optional<Partida> buscarPorSala(UUID idSala);
}
