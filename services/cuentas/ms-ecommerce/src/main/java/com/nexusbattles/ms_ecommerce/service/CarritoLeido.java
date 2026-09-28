package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.model.Carrito;
import com.nexusbattles.ms_ecommerce.model.ItemCarrito;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Lo que habia en el carrito al leerlo, fuera de la entidad JPA.
 *
 * <p>Existe para poder cerrar la transaccion antes de ponerle precio: el precio
 * sale del catalogo (una copia en memoria que, al caducar, se relee por HTTP),
 * y una llamada HTTP con la conexion a la base tomada es justo lo que el
 * carrito evitaba desde R16. Con esta foto, la transaccion lee y se cierra, y
 * el precio se calcula despues sin tocar la entidad ni sus relaciones
 * perezosas.
 */
public record CarritoLeido(Long id, String usuarioId, List<Linea> lineas) {

    public CarritoLeido {
        lineas = List.copyOf(lineas);
    }

    public static CarritoLeido de(Carrito carrito) {
        List<Linea> lineas = carrito.getItems().stream()
                .sorted(Comparator.comparing(ItemCarrito::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(item -> new Linea(item.getId(), item.getProductoRef(), item.getProductoNombre(),
                        Objects.requireNonNullElse(item.getCantidad(), 0), item.getPrecioUnitario(), item.getMoneda()))
                .toList();
        return new CarritoLeido(carrito.getId(), carrito.getUsuarioId(), lineas);
    }

    /** Las lineas de productos del catalogo, las unicas que se pueden comprar. */
    public List<Linea> delCatalogo() {
        return lineas.stream().filter(linea -> linea.productoRef() != null && linea.cantidad() > 0).toList();
    }

    /**
     * Una linea tal como esta guardada.
     *
     * @param precioUnitario la instantanea: el ultimo precio calculado al
     *                       escribirla, en {@code moneda} (COP)
     */
    public record Linea(Long id, String productoRef, String nombre, int cantidad, BigDecimal precioUnitario,
                        String moneda) {
    }
}
