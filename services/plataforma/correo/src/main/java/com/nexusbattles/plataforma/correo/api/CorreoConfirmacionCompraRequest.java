package com.nexusbattles.plataforma.correo.api;

import com.nexusbattles.plataforma.correo.cola.CorreoPedido;
import com.nexusbattles.plataforma.correo.template.Plantilla;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Correo de confirmación de un pago aprobado (HU-PAG-003, issue #537). Lo
 * dispara ms-finanzas (Grupo 4/Cuentas) tras aprobar el pago en su pasarela
 * simulada; este servicio no valida ni conoce la transacción en sí, solo la
 * transcribe al correo. monto/moneda/concepto siguen la misma forma que
 * {@code Transaccion} en ms-finanzas (moneda: ISO 4217 en mayúsculas).
 *
 * <p>Desde la 1.4.0 puede traer el detalle de los productos ({@code lineas}) y
 * la orden: el 7.5 del documento pide «correo electrónico de confirmación de
 * compra con el detalle de los productos adquiridos y el total pagado». Son
 * opcionales para no romper a quien ya llama sin ellos.
 */
public record CorreoConfirmacionCompraRequest(
        @NotBlank @Email String email,
        @NotBlank String apodo,
        @NotNull @Positive BigDecimal monto,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "debe ser un código ISO 4217 en mayúsculas, p. ej. COP") String moneda,
        @NotBlank String concepto,
        @NotNull OffsetDateTime fechaHora,
        List<@Valid @NotNull LineaDeCompra> lineas,
        String orden) {

    /**
     * Un producto de la compra, tal como lo cobro ms-finanzas.
     *
     * @param nombre         nombre del producto
     * @param cantidad       unidades
     * @param precioUnitario opcional
     * @param subtotal       lo cobrado por esta linea
     */
    public record LineaDeCompra(
            @NotBlank String nombre,
            @NotNull Integer cantidad,
            BigDecimal precioUnitario,
            @NotNull BigDecimal subtotal) {
    }

    /** El destinatario es una persona: no se le muestra un ISO-8601 crudo. */
    public String fechaHoraLegible() {
        return FechaLegible.de(fechaHora);
    }

    /** p. ej. "50000.00 COP" — sin símbolos de moneda que no cubran todas las divisas soportadas. */
    public String montoFormateado() {
        return importe(monto);
    }

    public CorreoPedido aCorreo() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("apodo", apodo);
        datos.put("monto", montoFormateado());
        datos.put("concepto", concepto);
        datos.put("fechaHora", fechaHoraLegible());
        if (lineas != null && !lineas.isEmpty()) {
            datos.put("lineas", lineas.stream().map(this::lineaParaElCorreo).toList());
        }
        if (orden != null && !orden.isBlank()) {
            datos.put("orden", orden.trim());
        }
        return CorreoPedido.paraEnviar(
                Plantilla.CONFIRMACION_COMPRA, email, "Confirmación de tu compra en The Nexus Battles VI", datos);
    }

    private Map<String, Object> lineaParaElCorreo(LineaDeCompra linea) {
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("nombre", linea.nombre());
        fila.put("cantidad", linea.cantidad());
        if (linea.precioUnitario() != null) {
            fila.put("precioUnitario", importe(linea.precioUnitario()));
        }
        fila.put("subtotal", importe(linea.subtotal()));
        return fila;
    }

    private String importe(BigDecimal valor) {
        return valor.toPlainString() + " " + moneda;
    }
}
