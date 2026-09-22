package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeVinculosDeTorneo;
import com.nexusbattles.plataforma.salaspartidas.dominio.VinculoDeTorneo;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class RepositorioVinculosDeTorneoJpa implements RepositorioDeVinculosDeTorneo {

    private final VinculosDeTorneoSpringData almacen;

    RepositorioVinculosDeTorneoJpa(VinculosDeTorneoSpringData almacen) {
        this.almacen = almacen;
    }

    @Override
    @Transactional
    public void guardar(VinculoDeTorneo vinculo) {
        // saveAndFlush: la clave foranea a `salas` se comprueba aqui, no al cerrar la transaccion.
        almacen.saveAndFlush(VinculoDeTorneoEntidad.desde(vinculo));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VinculoDeTorneo> buscarPorSala(UUID idSala) {
        return almacen.findById(idSala).map(VinculoDeTorneoEntidad::aDominio);
    }
}
