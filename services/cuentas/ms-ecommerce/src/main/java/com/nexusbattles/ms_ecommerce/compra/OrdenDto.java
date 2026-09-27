package com.nexusbattles.ms_ecommerce.compra;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Una orden tal como la ve el jugador ({@code Orden} del contrato 1.3.0/1.4.0).
 * Nunca la entidad: ni la clave de idempotencia, ni la traza, ni el estado de
 * los reintentos salen a la API.
 */
public record OrdenDto(
        UUID id,
        String estado,
        String moneda,
        BigDecimal total,
        BigDecimal tasaDeCambio,
        List<Linea> lineas,
        MedioDePago medioDePago,
        String motivo,
        String correoConfirmacion,
        Instant creadaEn,
        Instant cobradaEn,
        Instant actualizadaEn) {

    public OrdenDto {
        lineas = List.copyOf(lineas);
    }

    public record Linea(String productoId, String nombre, int cantidad, BigDecimal precioUnitario,
                        BigDecimal precioOriginal, Integer descuentoPorcentaje, BigDecimal subtotal) {
    }

    /** Solo la marca y los cuatro ultimos digitos: es todo lo que se guarda. */
    public record MedioDePago(String marca, String ultimos4) {
    }

    public static OrdenDto de(Orden orden) {
        List<Linea> lineas = orden.getLineas().stream()
                .map(linea -> new Linea(linea.getProductoRef(), linea.getNombre(), linea.getCantidad(),
                        linea.getPrecioUnitario(), linea.getPrecioOriginal(), linea.getDescuentoPorcentaje(),
                        linea.getSubtotal()))
                .toList();
        MedioDePago medio = orden.getMedioUltimos4() == null
                ? null
                : new MedioDePago(orden.getMedioMarca(), orden.getMedioUltimos4());
        return new OrdenDto(orden.getId(), orden.getEstado().name(), orden.getMoneda().name(), orden.getTotal(),
                orden.getTasaDeCambio(), lineas, medio, orden.getMotivo(), orden.getCorreo().name(),
                orden.getCreadaEn(), orden.getCobradaEn(), orden.getActualizadaEn());
    }
}
