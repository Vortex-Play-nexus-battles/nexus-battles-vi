package com.nexusbattles.ms_ecommerce.dto;

/**
 * El producto de una linea del carrito:
 * {@code {"id":"<uuid>","nombre":"...","moneda":"COP","imagen":"..."}}.
 *
 * @param id     el identificador del producto en el catalogo maestro; nulo en
 *               las lineas anteriores a V2, que apuntaban a la tabla local
 * @param moneda la moneda de los precios de la linea (la del carrito)
 * @param imagen (1.4.0) la del catalogo, para la vista desplegada del carrito
 *               (7.5: «con su respectiva imagen»); null si no se sabe
 */
public record ProductoDelItemDto(String id, String nombre, String moneda, String imagen) {

    /** Sin imagen: la forma anterior a 1.4.0. */
    public ProductoDelItemDto(String id, String nombre, String moneda) {
        this(id, nombre, moneda, null);
    }
}
