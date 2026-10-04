package com.nexusbattles.ms_ecommerce.compra;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Una orden tal como la ve el jugador ({@code Orden} del contrato 1.3.0/1.4.0).
 * Nunca la entidad: ni la clave de idempotencia, ni la traza, ni el estado de
 * los reintentos salen a la API.
 *
 * <p>1.6.0 (D-44): {@code formaDePago} dice como se pago. En una orden pagada
 * con creditos la {@code moneda} es {@link #CREDITOS} y los importes son
 * creditos enteros (la columna los guarda con dos decimales, todos a cero).
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
        Instant actualizadaEn,
        String formaDePago) {

    /** La unidad de los importes de una orden pagada con creditos (contrato 1.6.0). */
    public static final String CREDITOS = "CREDITOS";

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
        boolean enCreditos = orden.pagadaConCreditos();
        UnaryOperator<BigDecimal> importe = enCreditos ? OrdenDto::creditosEnteros : UnaryOperator.identity();
        List<Linea> lineas = orden.getLineas().stream()
                .map(linea -> new Linea(linea.getProductoRef(), linea.getNombre(), linea.getCantidad(),
                        importe.apply(linea.getPrecioUnitario()), importe.apply(linea.getPrecioOriginal()),
                        linea.getDescuentoPorcentaje(), importe.apply(linea.getSubtotal())))
                .toList();
        MedioDePago medio = orden.getMedioUltimos4() == null
                ? null
                : new MedioDePago(orden.getMedioMarca(), orden.getMedioUltimos4());
        String moneda = enCreditos ? CREDITOS : orden.getMoneda().name();
        return new OrdenDto(orden.getId(), orden.getEstado().name(), moneda, importe.apply(orden.getTotal()),
                orden.getTasaDeCambio(), lineas, medio, orden.getMotivo(), orden.getCorreo().name(),
                orden.getCreadaEn(), orden.getCobradaEn(), orden.getActualizadaEn(), orden.getFormaDePago().name());
    }

    /** 825.00 → 825: los creditos son enteros; la escala de la columna no es un dato. */
    private static BigDecimal creditosEnteros(BigDecimal valor) {
        return valor == null ? null : valor.setScale(0, RoundingMode.UNNECESSARY);
    }
}
