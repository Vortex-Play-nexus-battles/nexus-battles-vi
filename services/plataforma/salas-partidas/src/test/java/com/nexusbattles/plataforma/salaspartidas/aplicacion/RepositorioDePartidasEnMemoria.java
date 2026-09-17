package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble en memoria del almacen de partidas.
 *
 * <p>No es un mock: implementa el puerto y se comporta como un almacen, incluida
 * la unicidad de una partida por sala. El adaptador real contra PostgreSQL se
 * prueba aparte, contra una base de datos de verdad.
 */
class RepositorioDePartidasEnMemoria implements RepositorioDePartidas {

    private final Map<UUID, Partida> almacen = new LinkedHashMap<>();

    @Override
    public Partida guardar(Partida partida) {
        almacen.put(partida.id(), partida);
        return partida;
    }

    @Override
    public Optional<Partida> buscarPorId(UUID id) {
        return Optional.ofNullable(almacen.get(id));
    }

    @Override
    public Optional<Partida> buscarPorSala(UUID idSala) {
        return almacen.values().stream()
                .filter(partida -> partida.idSala().equals(idSala))
                .findFirst();
    }
}
