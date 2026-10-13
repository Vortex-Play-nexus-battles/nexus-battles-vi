package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.persistencia;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.RepositorioDeBloqueos;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Los bloqueos de los mensajes privados en PostgreSQL (V15, D-40).
 *
 * <p>Bloquear dos veces a la vez no es un error: la clave primaria
 * {@code (id_bloqueador, id_bloqueado)} se queda con una fila y el que pierde
 * la carrera la da por buena.
 */
@Repository
class RepositorioBloqueosJpa implements RepositorioDeBloqueos {

    private final BloqueosSpringData almacen;

    RepositorioBloqueosJpa(BloqueosSpringData almacen) {
        this.almacen = almacen;
    }

    /**
     * Sin {@code @Transactional}: si el insert choca con la clave, la
     * transaccion de {@code saveAndFlush} queda perdida y la comprobacion del
     * que gano tiene que ir en otra (igual que en los mensajes).
     */
    @Override
    public boolean bloquear(UUID quien, UUID aQuien, Instant cuando) {
        if (almacen.existsById(new BloqueoEntidad.Clave(quien, aQuien))) {
            return false;
        }
        try {
            almacen.saveAndFlush(new BloqueoEntidad(quien, aQuien, cuando));
            return true;
        } catch (DataIntegrityViolationException otroSeAdelanto) {
            return false;
        }
    }

    @Override
    @Transactional
    public boolean desbloquear(UUID quien, UUID aQuien) {
        BloqueoEntidad.Clave clave = new BloqueoEntidad.Clave(quien, aQuien);
        if (!almacen.existsById(clave)) {
            return false;
        }
        almacen.deleteById(clave);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean bloqueo(UUID quien, UUID aQuien) {
        return almacen.existsById(new BloqueoEntidad.Clave(quien, aQuien));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> bloqueadosPor(UUID quien) {
        return almacen.findByBloqueador(quien).stream().map(BloqueoEntidad::bloqueado).collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> quienesBloquearonA(UUID jugador) {
        return almacen.findByBloqueado(jugador).stream().map(BloqueoEntidad::bloqueador).collect(Collectors.toSet());
    }
}
