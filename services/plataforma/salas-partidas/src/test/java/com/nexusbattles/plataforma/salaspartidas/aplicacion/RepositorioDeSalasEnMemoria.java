package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble en memoria del repositorio, para probar el caso de uso sin base de datos.
 *
 * <p>No es un mock: implementa el puerto de verdad y se comporta como un almacen.
 * El adaptador real contra PostgreSQL llega en la siguiente etapa y se prueba
 * aparte, contra una base de datos real.
 */
class RepositorioDeSalasEnMemoria implements RepositorioDeSalas {

    private final Map<UUID, Sala> almacen = new LinkedHashMap<>();

    @Override
    public Sala guardar(Sala sala) {
        almacen.put(sala.id(), sala);
        return sala;
    }

    @Override
    public Optional<Sala> buscarPorId(UUID id) {
        return Optional.ofNullable(almacen.get(id));
    }

    int cuantasHay() {
        return almacen.size();
    }
}
