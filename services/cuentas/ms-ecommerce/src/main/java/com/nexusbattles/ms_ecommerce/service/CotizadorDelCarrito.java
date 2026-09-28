package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.catalogo.ProductoDelCatalogo;
import com.nexusbattles.ms_ecommerce.dto.CarritoDto;
import com.nexusbattles.ms_ecommerce.dto.ItemCarritoDto;
import com.nexusbattles.ms_ecommerce.dto.MotivoDeLinea;
import com.nexusbattles.ms_ecommerce.dto.ProductoDelItemDto;
import com.nexusbattles.ms_ecommerce.precios.CalculadoraDePrecios;
import com.nexusbattles.ms_ecommerce.precios.PrecioCalculado;
import com.nexusbattles.ms_ecommerce.precios.Tarifa;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * El carrito con precio: cada respuesta del carrito lo recalcula en el
 * servidor (contrato 1.4.0, 7.5: «visualizar el total a pagar»).
 *
 * <p>El precio de cada linea es el que el catalogo tiene AHORA —con su
 * promocion vigente y en la moneda pedida—, no el que tenia al anadirla: un
 * carrito que se guarda dias (7.5) no puede ensenar un precio que ya no es el
 * que se va a cobrar. La compra lo vuelve a calcular producto a producto en el
 * momento de pagar; este es el mismo calculo sobre la copia de 30 s.
 *
 * <p>Si el catalogo no responde, el carrito se sigue leyendo con la ultima
 * instantanea guardada en cada linea ({@code preciosVigentes: false}): el
 * carrito del jugador es suyo y no depende del catalogo para existir.
 */
@Component
public class CotizadorDelCarrito {

    /** Tope de unidades por linea (contrato 1.3.0, {@code PUT .../cantidad}: 1..20). */
    public static final int MAXIMO_POR_LINEA = 20;

    /**
     * @param catalogo los productos del catalogo por id, o vacio si no respondio
     */
    public CarritoDto cotizar(CarritoLeido carrito, Tarifa tarifa, Optional<Map<String, ProductoDelCatalogo>> catalogo,
                              Instant ahora) {
        List<ItemCarritoDto> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(tarifa.moneda().escala());
        int unidades = 0;
        for (CarritoLeido.Linea linea : carrito.lineas()) {
            ItemCarritoDto item = cotizarLinea(linea, tarifa, catalogo, ahora);
            items.add(item);
            unidades += linea.cantidad();
            if (item.disponible() && item.subtotal() != null) {
                total = total.add(item.subtotal());
            }
        }
        String moneda = items.isEmpty() ? null : tarifa.moneda().name();
        return new CarritoDto(carrito.id(), carrito.usuarioId(), items, total, moneda, unidades, catalogo.isPresent());
    }

    /**
     * Hasta cuantas unidades admite una linea de ese producto: 20, o las que le
     * quedan si son menos; 0 si esta agotado.
     */
    public static int maximoPara(ProductoDelCatalogo producto) {
        if (!producto.tieneExistencias()) {
            return 0;
        }
        return producto.tieneTirajeLimitado() ? Math.min(MAXIMO_POR_LINEA, producto.tiraje()) : MAXIMO_POR_LINEA;
    }

    private static ItemCarritoDto cotizarLinea(CarritoLeido.Linea linea, Tarifa tarifa,
                                               Optional<Map<String, ProductoDelCatalogo>> catalogo, Instant ahora) {
        String moneda = tarifa.moneda().name();
        if (linea.productoRef() == null) {
            // Linea legada (anterior a V2): no es un producto del catalogo.
            return deInstantanea(linea, tarifa, moneda, false, MotivoDeLinea.NO_DISPONIBLE, null);
        }
        if (catalogo.isEmpty()) {
            // El catalogo no respondio: la ultima instantanea, sin afirmar nada nuevo.
            return deInstantanea(linea, tarifa, moneda, true, null, null);
        }
        ProductoDelCatalogo producto = catalogo.get().get(linea.productoRef());
        if (producto == null || !producto.estaEnVenta()) {
            return deInstantanea(linea, tarifa, moneda, false, MotivoDeLinea.NO_DISPONIBLE, null);
        }
        if (!producto.tienePrecioEnMonedaReal()) {
            return deInstantanea(linea, tarifa, moneda, false, MotivoDeLinea.SIN_PRECIO_EN_MONEDA_REAL, null);
        }
        PrecioCalculado precio = CalculadoraDePrecios.deProducto(producto, tarifa, ahora);
        int maximo = maximoPara(producto);
        MotivoDeLinea motivo = null;
        if (maximo == 0) {
            motivo = MotivoDeLinea.AGOTADO;
        } else if (linea.cantidad() > maximo) {
            motivo = MotivoDeLinea.TIRAJE_INSUFICIENTE;
        }
        ProductoDelItemDto delItem = new ProductoDelItemDto(linea.productoRef(), producto.nombre(), moneda,
                producto.imagen());
        return new ItemCarritoDto(linea.id(), delItem, linea.cantidad(), precio.precioFinal(), precio.precioOriginal(),
                precio.porcentajeDescuento(), precio.subtotal(linea.cantidad()), motivo == null, motivo, maximo);
    }

    private static ItemCarritoDto deInstantanea(CarritoLeido.Linea linea, Tarifa tarifa, String moneda,
                                                boolean disponible, MotivoDeLinea motivo, Integer maximo) {
        BigDecimal unitario = linea.precioUnitario() == null
                ? null
                : CalculadoraDePrecios.convertir(linea.precioUnitario(), tarifa);
        BigDecimal subtotal = unitario == null ? null : unitario.multiply(BigDecimal.valueOf(linea.cantidad()));
        ProductoDelItemDto delItem = new ProductoDelItemDto(linea.productoRef(), linea.nombre(), moneda, null);
        boolean seCompra = disponible && unitario != null;
        MotivoDeLinea porQueNo = seCompra ? null : (motivo != null ? motivo : MotivoDeLinea.NO_DISPONIBLE);
        return new ItemCarritoDto(linea.id(), delItem, linea.cantidad(), unitario, unitario, null, subtotal,
                seCompra, porQueNo, maximo);
    }
}
