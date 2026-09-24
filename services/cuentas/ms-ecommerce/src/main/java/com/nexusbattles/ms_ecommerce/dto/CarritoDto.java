package com.nexusbattles.ms_ecommerce.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * El carrito tal como lo ve el jugador:
 * {@code {"id":1,"usuarioId":"<uid>","items":[...],"total":6000.00,"moneda":"COP"}}.
 *
 * <p>Hasta ahora los endpoints del carrito serializaban la entidad JPA tal
 * cual: cualquier columna nueva salia a la API sin querer, y cualquier
 * relacion perezosa podia reventar al serializar. Ahora sale este registro.
 *
 * @param moneda la moneda de sus lineas; nula si el carrito esta vacio, porque
 *               un carrito vacio no tiene moneda que declarar
 */
public record CarritoDto(
        Long id,
        String usuarioId,
        List<ItemCarritoDto> items,
        BigDecimal total,
        String moneda) {

    public CarritoDto {
        items = List.copyOf(items);
    }
}
