package com.nexusbattles.ms_subastas.subastas.dto;

import com.nexusbattles.ms_subastas.subastas.model.TipoProducto;

import java.math.BigDecimal;
import java.util.List;

/**
 * HU-SUB-011. Agrupa los parametros de filtro/orden del listado, tal como
 * los define contracts/openapi/ms-subastas-listado.yaml.
 *
 * tiempoRestante, tipoVenta, metodoPago, vendedor y ordenarPor viajan como
 * String (no enum de Java) a proposito: son los mismos valores literales
 * del contrato OpenAPI, y la validacion de que sean uno de los valores
 * permitidos ocurre en el controlador (donde Spring ya sabe mapear el
 * enum del contrato). Aqui solo se agrupan para pasarlos al servicio.
 */
public record FiltrosSubasta(
    String q,
    List<TipoProducto> tipoProducto,
    String rareza,
    BigDecimal precioMin,
    BigDecimal precioMax,
    String tiempoRestante,
    String tipoVenta,
    String metodoPago,
    String vendedor,
    String ordenarPor
) {
}
