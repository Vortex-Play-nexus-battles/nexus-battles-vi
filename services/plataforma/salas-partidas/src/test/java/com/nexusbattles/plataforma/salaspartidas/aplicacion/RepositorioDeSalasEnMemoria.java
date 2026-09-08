package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.PaginaDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;

import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * Misma regla que el adaptador real: solo salen los estados que el sistema
     * de diseno sabe pintar. Las privadas si aparecen.
     *
     * <p>Aqui se pagina sobre una lista porque son cuatro salas de prueba. El
     * adaptador contra PostgreSQL lo hace en la consulta, que es donde debe
     * hacerse, y se comprueba aparte con Testcontainers.
     */
    @Override
    public PaginaDeSalas listar(Modalidad modalidad, EstadoSala estado,
                                int pagina, int tamano) {
        List<Sala> coincidencias = almacen.values().stream()
                .filter(sala -> sala.estado().apareceEnElListado())
                .filter(sala -> modalidad == null || sala.modalidad() == modalidad)
                .filter(sala -> estado == null || sala.estado() == estado)
                .toList();

        int desde = Math.min(pagina * tamano, coincidencias.size());
        int hasta = Math.min(desde + tamano, coincidencias.size());
        int totalPaginas = (int) Math.ceil((double) coincidencias.size() / tamano);

        return new PaginaDeSalas(coincidencias.subList(desde, hasta),
                pagina, tamano, coincidencias.size(), totalPaginas);
    }

    int cuantasHay() {
        return almacen.size();
    }
}
