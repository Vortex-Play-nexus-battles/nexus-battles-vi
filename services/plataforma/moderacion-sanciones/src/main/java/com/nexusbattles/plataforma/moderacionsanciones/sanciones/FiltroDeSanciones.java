package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Los filtros del listado global de sanciones — SA-4 (informe de revision,
 * punto 20: «mejores filtros de busqueda»; §7.3.8 «estadisticas de sanciones
 * aplicadas por periodo»). Todos opcionales y combinados con Y.
 *
 * @param tipo      ADVERTENCIA, SUSPENSION o BANEO
 * @param causal    una de las ocho de §7.3.2
 * @param estado    que significa hoy: vigente, cumplida o revertida
 * @param usuarioId la cuenta sancionada
 * @param desde     emitidas desde este instante (incluido)
 * @param hasta     emitidas antes de este instante
 */
public record FiltroDeSanciones(Sancion.Tipo tipo, Causal causal, Estado estado, UUID usuarioId,
                                OffsetDateTime desde, OffsetDateTime hasta) {

    /**
     * Lo que una sancion significa hoy, con la misma regla que
     * {@link Sancion#estaVigenteEn}: vigente es «no revertida y, si es
     * suspension, no vencida»; cumplida, una suspension que ya termino; y
     * revertida, la levantada o revertida en apelacion.
     */
    public enum Estado { VIGENTE, CUMPLIDA, REVERTIDA }

    /** Sin filtros: todas. */
    public static FiltroDeSanciones ninguno() {
        return new FiltroDeSanciones(null, null, null, null, null, null);
    }

    /** La consulta para la base, con {@code ahora} como frontera de la vigencia. */
    Specification<Sancion> comoEspecificacion(OffsetDateTime ahora) {
        return (raiz, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (tipo != null) {
                condiciones.add(cb.equal(raiz.get("tipo"), tipo));
            }
            if (causal != null) {
                condiciones.add(cb.equal(raiz.get("causal"), causal));
            }
            if (usuarioId != null) {
                condiciones.add(cb.equal(raiz.get("usuarioId"), usuarioId));
            }
            if (desde != null) {
                condiciones.add(cb.greaterThanOrEqualTo(raiz.get("emitidaEn"), desde));
            }
            if (hasta != null) {
                condiciones.add(cb.lessThan(raiz.get("emitidaEn"), hasta));
            }
            if (estado != null) {
                Predicate noRevertida = cb.isNull(raiz.get("revertidaEn"));
                Predicate esSuspension = cb.equal(raiz.get("tipo"), Sancion.Tipo.SUSPENSION);
                condiciones.add(switch (estado) {
                    case REVERTIDA -> cb.isNotNull(raiz.get("revertidaEn"));
                    case CUMPLIDA -> cb.and(noRevertida, esSuspension,
                            cb.lessThanOrEqualTo(raiz.get("vigenteHasta"), ahora));
                    case VIGENTE -> cb.and(noRevertida, cb.or(cb.not(esSuspension),
                            cb.greaterThan(raiz.get("vigenteHasta"), ahora)));
                });
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
