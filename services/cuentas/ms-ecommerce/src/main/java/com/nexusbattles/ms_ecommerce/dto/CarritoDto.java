package com.nexusbattles.ms_ecommerce.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * El carrito tal como lo ve el jugador:
 * {@code {"id":1,"usuarioId":"<uid>","items":[...],"total":6000,"moneda":"COP","unidades":1,"preciosVigentes":true}}.
 *
 * <p>Hasta R16 los endpoints del carrito serializaban la entidad JPA tal
 * cual: cualquier columna nueva salia a la API sin querer, y cualquier
 * relacion perezosa podia reventar al serializar. Sale este registro.
 *
 * @param total           (1.4.0) suma de los subtotales de las lineas disponibles,
 *                        calculada por el servidor en {@code moneda}
 * @param moneda          la de sus precios; nula si el carrito esta vacio, porque
 *                        un carrito vacio no tiene moneda que declarar
 * @param unidades        (1.4.0) suma de las cantidades de todas las lineas
 * @param preciosVigentes (1.4.0) false si el catalogo no respondio y los precios
 *                        son los ultimos conocidos
 */
public record CarritoDto(
        Long id,
        String usuarioId,
        List<ItemCarritoDto> items,
        BigDecimal total,
        String moneda,
        int unidades,
        boolean preciosVigentes) {

    public CarritoDto {
        items = List.copyOf(items);
    }

    /** Con los precios del catalogo: la forma anterior a 1.4.0. */
    public CarritoDto(Long id, String usuarioId, List<ItemCarritoDto> items, BigDecimal total, String moneda) {
        this(id, usuarioId, items, total, moneda, unidadesDe(items), true);
    }

    private static int unidadesDe(List<ItemCarritoDto> items) {
        return items.stream().mapToInt(item -> item.cantidad() == null ? 0 : item.cantidad()).sum();
    }
}
