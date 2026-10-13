package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PaginaDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaModificadaConcurrentemente;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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

    /**
     * Guarda la partida con bloqueo optimista (V14, B7).
     *
     * <p>Mismo patron que {@code RepositorioSalasJpa}: la marca se comprueba
     * ANTES de escribir —los participantes son una coleccion que Hibernate
     * sincroniza con borrados e inserciones en el mismo flush, y una escritura
     * rechazada no debe tocar ninguna fila— y el {@code UPDATE ... WHERE
     * version = ?} con {@code saveAndFlush} cubre la carrera que se cuele entre
     * la comprobacion y el flush. Las dos se traducen a
     * {@link PartidaModificadaConcurrentemente}.
     *
     * @return la partida con la marca nueva: es la que hay que usar para seguir
     */
    @Override
    @Transactional
    public Partida guardar(Partida partida) {
        try {
            almacen.findById(partida.id()).ifPresent(actual -> {
                if (actual.version() != partida.version()) {
                    throw new PartidaModificadaConcurrentemente(partida.id());
                }
            });
            return almacen.saveAndFlush(PartidaEntidad.desde(partida)).aDominio();
        } catch (OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException otraSeAdelanto) {
            throw new PartidaModificadaConcurrentemente(partida.id());
        }
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

    @Override
    @Transactional(readOnly = true)
    public PaginaDePartidas buscarPorJugador(UUID idJugador, int pagina, int tamano) {
        Page<PartidaEntidad> resultado = almacen.delJugador(idJugador, PageRequest.of(pagina, tamano));
        return new PaginaDePartidas(
                resultado.getContent().stream().map(PartidaEntidad::aDominio).toList(),
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements(),
                resultado.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Partida> conTurnoVencido(Instant ahora) {
        return almacen.conTurnoVencido(EstadoPartida.EN_CURSO, ahora).stream()
                .map(PartidaEntidad::aDominio)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Partida> enCursoDesde(Instant limite, int lote) {
        return almacen.iniciadasAntesDe(EstadoPartida.EN_CURSO, limite,
                        org.springframework.data.domain.PageRequest.of(0, lote)).stream()
                .map(PartidaEntidad::aDominio)
                .toList();
    }
}
