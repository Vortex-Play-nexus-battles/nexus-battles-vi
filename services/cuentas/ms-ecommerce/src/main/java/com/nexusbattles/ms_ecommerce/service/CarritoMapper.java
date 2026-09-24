package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.dto.CarritoDto;
import com.nexusbattles.ms_ecommerce.dto.ItemCarritoDto;
import com.nexusbattles.ms_ecommerce.dto.ProductoDelItemDto;
import com.nexusbattles.ms_ecommerce.model.Carrito;
import com.nexusbattles.ms_ecommerce.model.ItemCarrito;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Entidad del carrito → {@link CarritoDto}.
 *
 * <p>Clase a mano y no MapStruct: este modulo no lo tiene configurado, y para
 * tres registros no merece la pena traerlo.
 */
@Component
public class CarritoMapper {

    public CarritoDto aDto(Carrito carrito) {
        List<ItemCarritoDto> items = carrito.getItems().stream()
                .map(CarritoMapper::aDto)
                .toList();
        return new CarritoDto(carrito.getId(), carrito.getUsuarioId(), items, carrito.getTotal(), monedaDe(items));
    }

    private static ItemCarritoDto aDto(ItemCarrito item) {
        ProductoDelItemDto producto = new ProductoDelItemDto(
                item.getProductoRef(), item.getProductoNombre(), item.getMoneda());
        return new ItemCarritoDto(item.getId(), producto, item.getCantidad(), item.getPrecioUnitario(), item.getSubtotal());
    }

    /**
     * La moneda del carrito es la de sus lineas: la primera que la declare.
     * Todas las lineas nuevas son COP; una linea legada sin precio no declara
     * ninguna y no cuenta. Sin lineas, nula.
     */
    private static String monedaDe(List<ItemCarritoDto> items) {
        return items.stream()
                .map(item -> item.producto().moneda())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
