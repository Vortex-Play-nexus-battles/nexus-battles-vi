package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.LiquidacionDeApuesta;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeLiquidaciones;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador del puerto de liquidaciones contra PostgreSQL — HU-JUE-014.
 *
 * <p>Traduce y nada mas. Que una liquidacion este pendiente o cerrada lo
 * decide {@code LiquidarApuesta}; aqui solo se recuerda.
 */
@Repository
class RepositorioLiquidacionesJpa implements RepositorioDeLiquidaciones {

    private final LiquidacionesSpringData almacen;

    RepositorioLiquidacionesJpa(LiquidacionesSpringData almacen) {
        this.almacen = almacen;
    }

    @Override
    @Transactional
    public LiquidacionDeApuesta guardar(LiquidacionDeApuesta liquidacion) {
        return almacen.save(LiquidacionEntidad.desde(liquidacion)).aDominio();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LiquidacionDeApuesta> buscarPorPartida(UUID idPartida) {
        return almacen.findById(idPartida).map(LiquidacionEntidad::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LiquidacionDeApuesta> pendientes(int maximo) {
        return almacen.findByEstadoOrderByCreadaEnAsc(
                        LiquidacionDeApuesta.Estado.PENDIENTE, PageRequest.of(0, Math.max(1, maximo)))
                .stream().map(LiquidacionEntidad::aDominio).toList();
    }
}
