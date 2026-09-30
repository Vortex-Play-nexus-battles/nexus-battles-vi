package com.nexusbattles.ms_subastas.panel.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexusbattles.ms_subastas.panel.model.PendienteDeRecoger;
import com.nexusbattles.ms_subastas.panel.model.Seguimiento;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Las respuestas de {@code ms-subastas-panel.yaml} 1.0.0. Los montos viajan
 * como cadena (un decimal en JSON pasa por el double de JavaScript) y las
 * fechas en ISO-8601, igual que en {@code ms-subastas-pujas.yaml}.
 */
public final class PanelDtos {

    private PanelDtos() {
    }

    /** «Mis subastas» (7.7.9). */
    public record MiPublicacion(
            UUID subastaId,
            String nombreProducto,
            String miniaturaUrl,
            String estado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal precioInicial,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal ofertaVigente,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal precioCompraInmediata,
            int cantidadPujas,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant fechaPublicacion,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant fechaFin,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant cerradaEn,
            int vistas,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal comisionCobrada,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal penalizacionCobrada,
            boolean cancelable,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal penalizacionSiCancela) {

        public static MiPublicacion de(Subasta s, boolean cancelable, BigDecimal penalizacionSiCancela) {
            return new MiPublicacion(s.getId(), s.getNombreProducto(), s.getMiniaturaUrl(), s.getEstado().name(),
                    s.getPrecioInicial(), s.getOfertaVigente(), s.getPrecioCompraInmediata(), s.getCantidadPujas(),
                    s.getFechaPublicacion(), s.getFechaFin(), s.getCerradaEn(), s.getVistas(),
                    s.getComisionCobrada(), s.getPenalizacionCobrada(), cancelable,
                    cancelable ? penalizacionSiCancela : null);
        }
    }

    /** Resultado de cancelar (7.7.10). */
    public record Cancelacion(
            UUID subastaId,
            String estado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal penalizacionCobrada,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant canceladaEn) {

        public static Cancelacion de(Subasta s) {
            return new Cancelacion(s.getId(), s.getEstado().name(),
                    s.getPenalizacionCobrada() == null ? BigDecimal.ZERO : s.getPenalizacionCobrada(),
                    s.getCerradaEn());
        }
    }

    /** Una subasta de la lista de seguimiento (7.7.9). */
    public record SubastaSeguida(
            UUID subastaId,
            String nombreProducto,
            String miniaturaUrl,
            String estado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal ofertaVigente,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal precioCompraInmediata,
            int cantidadPujas,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant fechaFin,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant seguidaDesde) {

        public static SubastaSeguida de(Subasta s, Seguimiento seguimiento) {
            return new SubastaSeguida(s.getId(), s.getNombreProducto(), s.getMiniaturaUrl(), s.getEstado().name(),
                    s.getOfertaVigente(), s.getPrecioCompraInmediata(), s.getCantidadPujas(), s.getFechaFin(),
                    seguimiento.getCreadoEn());
        }
    }

    /** Un producto ganado pendiente de recoger (7.7.9). */
    public record Pendiente(
            UUID subastaId,
            String nombreProducto,
            String miniaturaUrl,
            String elementoInventarioId,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal montoPagado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant ganadaEn,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant venceEn,
            String estado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant resueltoEn) {

        public static Pendiente de(PendienteDeRecoger p, Subasta s) {
            return new Pendiente(p.getSubastaId(), s == null ? null : s.getNombreProducto(),
                    s == null ? null : s.getMiniaturaUrl(), p.getElementoInventarioId(), p.getMontoPagado(),
                    p.getGanadaEn(), p.getVenceEn(), p.getEstado().name(), p.getResueltoEn());
        }
    }

    /** «Recoger todo»: lo que se recogio y lo que no. */
    public record ResultadoDeRecogida(List<Pendiente> recogidos, List<Fallo> fallidos) {
        public record Fallo(UUID subastaId, String detalle) {
        }
    }

    /** Una linea del historial de transacciones (7.7.9). */
    public record Movimiento(
            String tipo,
            UUID subastaId,
            String nombreProducto,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal monto,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant fecha) {
    }

    /** El historial con sus totales. */
    public record Historial(
            List<Movimiento> movimientos,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalGanado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalGastado,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal comisionesPagadas,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance) {
    }
}
