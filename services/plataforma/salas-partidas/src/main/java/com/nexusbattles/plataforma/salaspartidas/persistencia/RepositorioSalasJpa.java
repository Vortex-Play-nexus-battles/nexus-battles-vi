package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.PaginaDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de salida: implementa el puerto del dominio contra PostgreSQL.
 *
 * <p>Su unico trabajo es traducir entre {@link Sala} y {@link SalaEntidad}. No
 * toma decisiones: si aqui apareciera una regla del juego, estaria en el sitio
 * equivocado.
 */
@Repository
public class RepositorioSalasJpa implements RepositorioDeSalas {

    private final SalasSpringData datos;

    RepositorioSalasJpa(SalasSpringData datos) {
        this.datos = datos;
    }

    /**
     * Guarda la sala y sus participantes en la misma transaccion.
     *
     * <p>Desde HU-SAL-002 no es una sola fila: la sala y su coleccion de
     * participantes tienen que escribirse juntas o no escribirse. Media
     * operacion dejaria un aforo que no coincide con quienes estan dentro, que
     * es justo lo que este incremento vino a impedir.
     */
    @Override
    @Transactional
    public Sala guardar(Sala sala) {
        datos.save(SalaEntidad.desde(sala));
        return sala;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Sala> buscarPorId(UUID id) {
        return datos.findById(id).map(SalaEntidad::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaDeSalas listar(Modalidad modalidad, EstadoSala estado,
                                int pagina, int tamano) {

        Page<SalaEntidad> resultado = datos.listar(EstadoSala.delListado(),
                modalidad, estado, PageRequest.of(pagina, tamano));

        return new PaginaDeSalas(
                resultado.getContent().stream().map(SalaEntidad::aDominio).toList(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages());
    }
}
