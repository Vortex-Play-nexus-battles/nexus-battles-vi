package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador del puerto de partidas contra PostgreSQL.
 *
 * <p>Traduce entre el dominio y la entidad, y nada mas: aqui no hay reglas de
 * combate. Si apareciera una, estaria en el sitio equivocado.
 */
@Repository
class RepositorioPartidasJpa implements RepositorioDePartidas {

    private final PartidasSpringData almacen;

    RepositorioPartidasJpa(PartidasSpringData almacen) {
        this.almacen = almacen;
    }

    @Override
    @Transactional
    public Partida guardar(Partida partida) {
        return almacen.save(PartidaEntidad.desde(partida)).aDominio();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Partida> buscarPorId(UUID id) {
        return almacen.findById(id).map(PartidaEntidad::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Partida> buscarPorSala(UUID idSala) {
        return almacen.findByIdSala(idSala).map(PartidaEntidad::aDominio);
    }
}
