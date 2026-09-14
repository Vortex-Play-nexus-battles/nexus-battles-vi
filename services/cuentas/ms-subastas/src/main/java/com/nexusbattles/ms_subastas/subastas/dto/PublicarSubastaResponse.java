package com.nexusbattles.ms_subastas.subastas.dto;

import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PublicarSubastaResponse(UUID id, UUID productoId, String elementoInventarioId,
        UUID vendedorId, BigDecimal precioInicial, BigDecimal ofertaVigente,
        BigDecimal precioCompraInmediata, String estado, Instant fechaPublicacion,
        Instant fechaFin, BigDecimal comisionCobrado, String nombreProducto,
        String tipoProducto, String rareza, String miniaturaUrl,
        String descripcionCorta, String habilidades) {
    public static PublicarSubastaResponse desde(Subasta s, BigDecimal comision) {
        return new PublicarSubastaResponse(s.getId(), s.getProductoId(), s.getElementoInventarioId(),
                s.getVendedorId(), s.getPrecioInicial(), s.getOfertaVigente(), s.getPrecioCompraInmediata(),
                s.getEstado().name(), s.getFechaPublicacion(), s.getFechaFin(), comision,
                s.getNombreProducto(), s.getTipoProducto() == null ? null : s.getTipoProducto().name(),
                s.getRareza(), s.getMiniaturaUrl(), s.getDescripcionCorta(), s.getHabilidades());
    }
}
