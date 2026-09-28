package com.nexusbattles.ms_subastas.subastas.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * La ficha de una subasta en cualquier estado ({@code SubastaDetalle} de
 * {@code ms-subastas-listado.yaml} 1.1.0): la «Vista de detalle del producto»
 * de 7.7.9 con lo que este servicio sabe de la subasta y del vendedor.
 */
public record SubastaDetalleResponse(
        UUID id,
        String estado,
        UUID productoId,
        String nombreProducto,
        String tipoProducto,
        String rareza,
        String miniaturaUrl,
        String descripcionCorta,
        String habilidades,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal precioInicial,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal ofertaVigente,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal pujaMinimaSiguiente,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal incrementoMinimo,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal precioCompraInmediata,
        boolean compraInmediataDisponible,
        int cantidadPujas,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant fechaPublicacion,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant fechaFin,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant cerradaEn,
        boolean esMaestroDeJuego,
        String metodoPago,
        UUID vendedorId,
        String vendedorApodo,
        Reputacion reputacionVendedor,
        int vistas) {

    /**
     * «Calificacion del vendedor basada en transacciones previas» (7.7.9).
     * {@code tasaDeExito} es la de 7.7.12 (ventas entre subastas terminadas);
     * nula si todavia no termino ninguna. Pasarla a estrellas, si se pasa, es
     * decision del Product Owner.
     */
    public record Reputacion(long ventasCompletadas, long subastasTerminadas, long cancelaciones,
                             Double tasaDeExito) {

        public static Reputacion de(long adjudicadas, long sinAdjudicacion, long canceladas) {
            long terminadas = adjudicadas + sinAdjudicacion + canceladas;
            Double tasa = terminadas == 0 ? null
                    : Math.round((double) adjudicadas / terminadas * 100.0) / 100.0;
            return new Reputacion(adjudicadas, terminadas, canceladas, tasa);
        }
    }

    public static SubastaDetalleResponse desde(Subasta s, Reputacion reputacion, int vistas) {
        return new SubastaDetalleResponse(s.getId(), s.getEstado().name(), s.getProductoId(), s.getNombreProducto(),
                s.getTipoProducto() == null ? null : s.getTipoProducto().name(), s.getRareza(), s.getMiniaturaUrl(),
                s.getDescripcionCorta(), s.getHabilidades(), s.getPrecioInicial(), s.getOfertaVigente(),
                s.estaActiva() ? s.pujaMinimaSiguiente() : null, s.getIncrementoMinimo(),
                s.getPrecioCompraInmediata(), s.compraInmediataDisponible(), s.getCantidadPujas(),
                s.getFechaPublicacion(), s.getFechaFin(), s.getCerradaEn(), s.isEsMaestroDeJuego(),
                // Solo el Maestro de Juego vende en dinero real (7.7.3); el mismo
                // criterio que ya usa el filtro metodoPago del listado.
                s.isEsMaestroDeJuego() ? "DINERO_REAL" : "CREDITOS",
                s.getVendedorId(), s.getApodoVendedor(), reputacion, vistas);
    }
}
