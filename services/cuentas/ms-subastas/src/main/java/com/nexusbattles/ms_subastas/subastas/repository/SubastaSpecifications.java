package com.nexusbattles.ms_subastas.subastas.repository;

import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.model.TipoProducto;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Filtros de HU-SUB-011 como Specification<Subasta> independientes y
 * combinables con .and(...). Mismo patron que AuditLogSpecifications en
 * ms-cumplimiento.
 *
 * Cada metodo devuelve null si el filtro no aplica (parametro vacio/nulo).
 * IMPORTANTE: Specification.and(null) SI lanza IllegalArgumentException --
 * quien combine estas specifications debe filtrar los null explicitamente
 * antes (ver SubastaListadoService.listar), no encadenar .and() directo.
 *
 * tipoVenta y metodoPago no son columnas propias: se derivan de
 * precioCompraInmediata y esMaestroDeJuego (ver contrato OpenAPI).
 */
public final class SubastaSpecifications {

    private SubastaSpecifications() {
    }

    public static Specification<Subasta> soloActivas() {
        return (root, query, cb) -> cb.equal(root.get("estado"), EstadoSubasta.ACTIVA);
    }

    /** Busca en nombre, descripcion, habilidades y tipo de producto. */
    public static Specification<Subasta> textoLibre(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String patron = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
            cb.like(cb.lower(root.get("nombreProducto")), patron),
            cb.like(cb.lower(root.get("descripcionCorta")), patron),
            cb.like(cb.lower(root.get("habilidades")), patron),
            cb.like(cb.lower(root.get("tipoProducto").as(String.class)), patron)
        );
    }

    public static Specification<Subasta> porTipoProducto(List<TipoProducto> tipos) {
        if (tipos == null || tipos.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("tipoProducto").in(tipos);
    }

    public static Specification<Subasta> porRareza(String rareza) {
        if (rareza == null || rareza.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("rareza"), rareza);
    }

    public static Specification<Subasta> precioMinimo(BigDecimal precioMin) {
        if (precioMin == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("ofertaVigente"), precioMin);
    }

    public static Specification<Subasta> precioMaximo(BigDecimal precioMax) {
        if (precioMax == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("ofertaVigente"), precioMax);
    }

    /**
     * Buckets del SRS 7.7.9: finaliza pronto / <1h / <6h / <24h. El SRS no
     * fija el umbral exacto de "finaliza pronto" -- se asume 15 minutos por
     * ahora. PENDIENTE de confirmar con el cliente, igual que el resto de
     * preguntas de producto que quedaron abiertas.
     */
    public static Specification<Subasta> tiempoRestante(String bucket, Instant ahora) {
        if (bucket == null || bucket.isBlank()) {
            return null;
        }
        Instant limite = switch (bucket) {
            case "FINALIZA_PRONTO" -> ahora.plus(15, ChronoUnit.MINUTES);
            case "MENOS_1H" -> ahora.plus(1, ChronoUnit.HOURS);
            case "MENOS_6H" -> ahora.plus(6, ChronoUnit.HOURS);
            case "MENOS_24H" -> ahora.plus(24, ChronoUnit.HOURS);
            default -> null;
        };
        if (limite == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("fechaFin"), limite);
    }

    /** SOLO_PUJAS = sin compra inmediata; COMPRA_INMEDIATA_DISPONIBLE = con ella. */
    public static Specification<Subasta> porTipoVenta(String tipoVenta) {
        if (tipoVenta == null || tipoVenta.isBlank()) {
            return null;
        }
        return switch (tipoVenta) {
            case "COMPRA_INMEDIATA_DISPONIBLE" ->
                (root, query, cb) -> cb.isNotNull(root.get("precioCompraInmediata"));
            case "SOLO_PUJAS" ->
                (root, query, cb) -> cb.isNull(root.get("precioCompraInmediata"));
            default -> null;
        };
    }

    /** Jugadores pagan en creditos; solo el Maestro de Juego vende en dinero real. */
    public static Specification<Subasta> porMetodoPago(String metodoPago) {
        if (metodoPago == null || metodoPago.isBlank()) {
            return null;
        }
        return switch (metodoPago) {
            case "DINERO_REAL" -> (root, query, cb) -> cb.isTrue(root.get("esMaestroDeJuego"));
            case "CREDITOS" -> (root, query, cb) -> cb.isFalse(root.get("esMaestroDeJuego"));
            default -> null;
        };
    }

    /** Mismo campo que porMetodoPago, expuesto tambien como filtro de "quien vende". */
    public static Specification<Subasta> porVendedor(String vendedor) {
        if (vendedor == null || vendedor.isBlank()) {
            return null;
        }
        return switch (vendedor) {
            case "MAESTRO_DE_JUEGO" -> (root, query, cb) -> cb.isTrue(root.get("esMaestroDeJuego"));
            case "JUGADORES" -> (root, query, cb) -> cb.isFalse(root.get("esMaestroDeJuego"));
            default -> null;
        };
    }
}
