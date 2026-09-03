package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.springframework.stereotype.Repository;

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

    @Override
    public Sala guardar(Sala sala) {
        datos.save(SalaEntidad.desde(sala));
        return sala;
    }

    @Override
    public Optional<Sala> buscarPorId(UUID id) {
        return datos.findById(id).map(SalaEntidad::aDominio);
    }
}
