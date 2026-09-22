package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.LiquidacionDeApuesta;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeLiquidaciones;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Almacen de liquidaciones en memoria, con el orden que promete el puerto. */
public class RepositorioDeLiquidacionesEnMemoria implements RepositorioDeLiquidaciones {

    final Map<UUID, LiquidacionDeApuesta> filas = new LinkedHashMap<>();

    @Override
    public LiquidacionDeApuesta guardar(LiquidacionDeApuesta liquidacion) {
        filas.put(liquidacion.idPartida(), liquidacion);
        return liquidacion;
    }

    @Override
    public Optional<LiquidacionDeApuesta> buscarPorPartida(UUID idPartida) {
        return Optional.ofNullable(filas.get(idPartida));
    }

    @Override
    public List<LiquidacionDeApuesta> pendientes(int maximo) {
        return filas.values().stream()
                .filter(l -> l.estado() == LiquidacionDeApuesta.Estado.PENDIENTE)
                .sorted(Comparator.comparing(LiquidacionDeApuesta::creadaEn))
                .limit(maximo)
                .toList();
    }
}
