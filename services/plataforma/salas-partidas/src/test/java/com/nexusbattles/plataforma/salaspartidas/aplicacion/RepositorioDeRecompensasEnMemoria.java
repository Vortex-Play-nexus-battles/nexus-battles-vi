package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.RecompensaDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeRecompensas;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Almacen de recompensas en memoria, con el orden que promete el puerto. */
public class RepositorioDeRecompensasEnMemoria implements RepositorioDeRecompensas {

    final Map<UUID, RecompensaDePartida> filas = new LinkedHashMap<>();

    @Override
    public RecompensaDePartida guardar(RecompensaDePartida recompensa) {
        filas.put(recompensa.idPartida(), recompensa);
        return recompensa;
    }

    @Override
    public Optional<RecompensaDePartida> buscarPorPartida(UUID idPartida) {
        return Optional.ofNullable(filas.get(idPartida));
    }

    @Override
    public List<RecompensaDePartida> pendientes(int maximo) {
        return filas.values().stream()
                .filter(r -> r.estado() == RecompensaDePartida.Estado.PENDIENTE)
                .sorted(Comparator.comparing(RecompensaDePartida::creadaEn))
                .limit(maximo)
                .toList();
    }
}
