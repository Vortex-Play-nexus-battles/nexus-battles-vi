package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.RecompensaDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeRecompensas;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador del puerto de recompensas contra PostgreSQL — HU-JUE-012.
 *
 * <p>Traduce y nada mas. Que una recompensa este pendiente o acreditada lo
 * decide {@code AcreditarRecompensa}; aqui solo se recuerda.
 */
@Repository
class RepositorioRecompensasJpa implements RepositorioDeRecompensas {

    private final RecompensasSpringData almacen;

    RepositorioRecompensasJpa(RecompensasSpringData almacen) {
        this.almacen = almacen;
    }

    @Override
    @Transactional
    public RecompensaDePartida guardar(RecompensaDePartida recompensa) {
        return almacen.save(RecompensaEntidad.desde(recompensa)).aDominio();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecompensaDePartida> buscarPorPartida(UUID idPartida) {
        return almacen.findById(idPartida).map(RecompensaEntidad::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecompensaDePartida> pendientes(int maximo) {
        return almacen.findByEstadoOrderByCreadaEnAsc(
                        RecompensaDePartida.Estado.PENDIENTE, PageRequest.of(0, Math.max(1, maximo)))
                .stream().map(RecompensaEntidad::aDominio).toList();
    }
}
