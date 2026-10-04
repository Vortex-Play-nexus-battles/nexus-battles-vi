package com.nexusbattles.ms_ecommerce.compra;

import com.nexusbattles.ms_ecommerce.catalogo.ProductoDelCatalogo;
import com.nexusbattles.ms_ecommerce.compra.CotizacionEnCreditos.Linea;
import com.nexusbattles.ms_ecommerce.compra.CotizacionEnCreditos.Motivo;
import com.nexusbattles.ms_ecommerce.precios.CalculadoraDePrecios;
import com.nexusbattles.ms_ecommerce.precios.PrecioEnCreditos;
import com.nexusbattles.ms_ecommerce.service.CarritoLeido;
import com.nexusbattles.ms_ecommerce.service.CotizadorDelCarrito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * La cotizacion del carrito en creditos del juego (D-44): cada linea con el
 * {@code precioCreditos} del catalogo (promocion vigente incluida), el total y
 * el saldo antes y despues. Una funcion pura: el carrito, el catalogo y el
 * saldo los trae quien llama, y aqui solo se calcula.
 *
 * <p>Una linea se puede comprar con el mismo criterio que el carrito y la
 * compra: el producto esta en el catalogo, a la venta (ACTIVO o UNICO), le
 * quedan unidades y no se piden mas de las que admite la linea. Lo que se
 * cobra lo vuelve a comprobar {@code POST /checkout/creditos} con el catalogo
 * de ese momento: esta cotizacion no aparta nada.
 */
public final class CotizadorEnCreditos {

    private CotizadorEnCreditos() {
    }

    /**
     * @param carrito         el carrito leido
     * @param catalogo        los productos del catalogo por id
     * @param saldoDisponible el saldo disponible en ms-finanzas, o vacio si no respondio
     * @param ahora           el instante con el que se evalua la promocion
     */
    public static CotizacionEnCreditos cotizar(CarritoLeido carrito, Map<String, ProductoDelCatalogo> catalogo,
                                               OptionalLong saldoDisponible, Instant ahora) {
        Long saldo = saldoDisponible.isPresent() ? saldoDisponible.getAsLong() : null;
        List<CarritoLeido.Linea> delCatalogo = carrito.delCatalogo();
        if (delCatalogo.isEmpty()) {
            return new CotizacionEnCreditos(false, Motivo.CARRITO_VACIO, List.of(), null, saldo, null, null);
        }
        List<Linea> lineas = new ArrayList<>();
        boolean todasDisponibles = true;
        boolean todasConPrecio = true;
        long total = 0;
        for (CarritoLeido.Linea linea : delCatalogo) {
            ProductoDelCatalogo producto = catalogo.get(linea.productoRef());
            boolean disponible = sePuedeComprar(producto, linea.cantidad());
            Optional<PrecioEnCreditos> precio = producto == null
                    ? Optional.empty()
                    : CalculadoraDePrecios.enCreditos(producto, ahora);
            String nombre = producto != null && producto.nombre() != null
                    ? producto.nombre()
                    : Objects.requireNonNullElse(linea.nombre(), "El producto");
            Long unitario = precio.map(PrecioEnCreditos::precioFinal).orElse(null);
            Long subtotal = precio.map(p -> p.subtotal(linea.cantidad())).orElse(null);
            lineas.add(new Linea(linea.productoRef(), nombre, linea.cantidad(), disponible, unitario, subtotal,
                    precio.map(PrecioEnCreditos::porcentajeDescuento).orElse(null)));
            todasDisponibles &= disponible;
            todasConPrecio &= subtotal != null;
            if (subtotal != null) {
                total = Math.addExact(total, subtotal);
            }
        }
        Motivo motivo = !todasDisponibles ? Motivo.PRODUCTO_NO_DISPONIBLE
                : !todasConPrecio ? Motivo.SIN_PRECIO_EN_CREDITOS
                : null;
        if (motivo != null) {
            return new CotizacionEnCreditos(false, motivo, lineas, null, saldo, null, null);
        }
        Long despues = saldo == null ? null : saldo - total;
        Boolean alcanza = despues == null ? null : despues >= 0;
        return new CotizacionEnCreditos(true, null, lineas, total, saldo, despues, alcanza);
    }

    /** Esta en el catalogo, a la venta, con unidades, y la cantidad cabe en la linea. */
    static boolean sePuedeComprar(ProductoDelCatalogo producto, int cantidad) {
        if (producto == null || !producto.estaEnVenta() || !producto.tieneExistencias()) {
            return false;
        }
        return cantidad >= 1 && cantidad <= CotizadorDelCarrito.maximoPara(producto);
    }
}
