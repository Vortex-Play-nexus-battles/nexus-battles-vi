package com.nexusbattles.ms_subastas.subastas.dto;

import com.nexusbattles.ms_subastas.subastas.model.Subasta;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * HU-SUB-011. Una fila del listado paginado -- corresponde al schema
 * SubastaResumen de contracts/openapi/ms-subastas-listado.yaml.
 *
 * vendedorId como String a proposito: su tipo real (Long/UUID publicId)
 * depende de la decision pendiente sobre el identificador estable en el
 * JWT (ver discusion con Andres/Santiago). Hoy viene directo de
 * Subasta.vendedorId (UUID), que a su vez es un campo sin resolver del
 * lado de quien publica -- no asumir que es definitivo.
 */
public record SubastaResumenResponse(
    UUID id,
    String nombreProducto,
    String tipoProducto,
    String rareza,
    String miniaturaUrl,
    BigDecimal precioInicial,
    BigDecimal ofertaVigente,
    BigDecimal precioCompraInmediata,
    int cantidadPujas,
    Instant fechaFin,
    boolean esMaestroDeJuego,
    String vendedorId
) {

    public static SubastaResumenResponse desde(Subasta subasta) {
        return new SubastaResumenResponse(
            subasta.getId(),
            subasta.getNombreProducto(),
            subasta.getTipoProducto() != null ? subasta.getTipoProducto().name() : null,
            subasta.getRareza(),
            subasta.getMiniaturaUrl(),
            subasta.getPrecioInicial(),
            subasta.getOfertaVigente(),
            subasta.getPrecioCompraInmediata(),
            subasta.getCantidadPujas(),
            subasta.getFechaFin(),
            subasta.isEsMaestroDeJuego(),
            subasta.getVendedorId() != null ? subasta.getVendedorId().toString() : null
        );
    }
}
