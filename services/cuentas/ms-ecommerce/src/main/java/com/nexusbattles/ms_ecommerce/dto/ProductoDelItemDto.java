package com.nexusbattles.ms_ecommerce.dto;

/**
 * El producto de una linea del carrito, tal como se guardo al agregarlo:
 * {@code {"id":"<uuid>","nombre":"...","moneda":"COP"}}.
 *
 * @param id     el identificador del producto en el catalogo maestro; nulo en
 *               las lineas anteriores a V2, que apuntaban a la tabla local
 * @param moneda la moneda de {@code precioUnitario}
 */
public record ProductoDelItemDto(String id, String nombre, String moneda) {
}
