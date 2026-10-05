package com.nexusbattles.ms_ecommerce.precios;

import com.nexusbattles.ms_ecommerce.catalogo.ProductoDelCatalogo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * El precio que se paga, calculado siempre en el servidor (B5, 7.5).
 *
 * <p>El navegador nunca manda un precio: la vitrina, el carrito y la compra
 * pasan por aqui con el precio del catalogo, y lo que llegue de fuera se
 * ignora. Las reglas, en este orden y sin excepciones:
 *
 * <ol>
 *   <li><b>Base en pesos enteros.</b> El catalogo publica
 *       {@code precioMonedaReal} en COP; se redondea a pesos (mitad hacia
 *       arriba), porque el peso no usa centavos en la practica.</li>
 *   <li><b>Promocion en COP.</b> Si hay una vigente, {@code base * (100 - %) / 100},
 *       otra vez a pesos enteros (mitad hacia arriba). El porcentaje se aplica
 *       en la moneda del catalogo y no despues de convertir, para que el
 *       descuento sea el mismo en las tres monedas.</li>
 *   <li><b>Conversion.</b> En USD o EUR, pesos entre la tasa (pesos por una
 *       unidad), a centavos (mitad hacia arriba): un solo redondeo por
 *       importe.</li>
 *   <li><b>Nunca gratis por redondeo.</b> Un precio positivo no baja de la
 *       unidad minima de su moneda (1 COP, 0,01 USD/EUR): «0,00 USD» seria
 *       regalar algo que tiene precio.</li>
 * </ol>
 *
 * <p>El subtotal de una linea es este precio unitario por la cantidad, y el
 * total la suma de subtotales ({@link PrecioCalculado#subtotal(int)}): ninguno
 * se vuelve a redondear, asi que el total cuadra siempre con lo que se ve.
 */
public final class CalculadoraDePrecios {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private CalculadoraDePrecios() {
    }

    /**
     * @param precioCop          el {@code precioMonedaReal} del catalogo, mayor que cero
     * @param porcentajeVigente  el de la promocion vigente (1 a 99), o null
     * @param tarifa             la moneda de destino y su tasa
     * @throws IllegalArgumentException si el precio no es positivo o el
     *         porcentaje no esta entre 1 y 99
     */
    public static PrecioCalculado calcular(BigDecimal precioCop, Integer porcentajeVigente, Tarifa tarifa) {
        Objects.requireNonNull(precioCop, "precioCop");
        Objects.requireNonNull(tarifa, "tarifa");
        if (precioCop.signum() <= 0) {
            throw new IllegalArgumentException("Un precio en moneda real tiene que ser mayor que cero");
        }
        if (porcentajeVigente != null && (porcentajeVigente < 1 || porcentajeVigente > 99)) {
            throw new IllegalArgumentException("Un descuento va de 1 a 99 %: " + porcentajeVigente);
        }
        BigDecimal baseCop = alMinimo(precioCop.setScale(0, RoundingMode.HALF_UP), Moneda.COP);
        BigDecimal finalCop = porcentajeVigente == null
                ? baseCop
                : alMinimo(baseCop.multiply(CIEN.subtract(BigDecimal.valueOf(porcentajeVigente)))
                        .divide(CIEN, 0, RoundingMode.HALF_UP), Moneda.COP);
        return new PrecioCalculado(tarifa.moneda(), convertir(baseCop, tarifa), convertir(finalCop, tarifa),
                porcentajeVigente);
    }

    /**
     * El precio de un producto del catalogo en la moneda de la tarifa, con la
     * promocion que este vigente en ese instante.
     *
     * @throws IllegalArgumentException si el producto no tiene precio en
     *         moneda real (quien llama ya lo tiene que haber descartado)
     */
    public static PrecioCalculado deProducto(ProductoDelCatalogo producto, Tarifa tarifa, Instant ahora) {
        return calcular(producto.precioMonedaReal(), producto.porcentajeVigenteEn(ahora), tarifa);
    }

    /**
     * Un importe en COP expresado en la moneda de la tarifa, con su escala.
     * Sirve tambien para las instantaneas del carrito, que se guardan en COP.
     */
    public static BigDecimal convertir(BigDecimal importeCop, Tarifa tarifa) {
        Moneda moneda = tarifa.moneda();
        if (moneda.esLaDelCatalogo()) {
            return importeCop.setScale(moneda.escala(), RoundingMode.HALF_UP);
        }
        BigDecimal convertido = importeCop.divide(tarifa.copPorUnidad(), moneda.escala(), RoundingMode.HALF_UP);
        return importeCop.signum() > 0 ? alMinimo(convertido, moneda) : convertido;
    }

    // ------------------------------------------------- D-44: precio en creditos

    /**
     * El precio en creditos de una unidad (D-44): el {@code precioCreditos} del
     * catalogo, sin convertir nada desde COP. Con una promocion vigente,
     * {@code base * (100 - %) / 100} a creditos enteros (mitad hacia arriba),
     * nunca por debajo de 1: el mismo porcentaje que en dinero real.
     *
     * @param precioCreditos    el del catalogo, mayor que cero
     * @param porcentajeVigente el de la promocion vigente (1 a 99), o null
     * @throws IllegalArgumentException si el precio no es positivo o el
     *         porcentaje no esta entre 1 y 99
     */
    public static long calcularEnCreditos(int precioCreditos, Integer porcentajeVigente) {
        if (precioCreditos < 1) {
            throw new IllegalArgumentException("Un precio en creditos tiene que ser mayor que cero");
        }
        if (porcentajeVigente == null) {
            return precioCreditos;
        }
        if (porcentajeVigente < 1 || porcentajeVigente > 99) {
            throw new IllegalArgumentException("Un descuento va de 1 a 99 %: " + porcentajeVigente);
        }
        long rebajado = BigDecimal.valueOf(precioCreditos)
                .multiply(CIEN.subtract(BigDecimal.valueOf(porcentajeVigente)))
                .divide(CIEN, 0, RoundingMode.HALF_UP)
                .longValueExact();
        return Math.max(1, rebajado);
    }

    /**
     * El precio en creditos de un producto del catalogo, con la promocion que
     * este vigente en ese instante; vacio si el producto no se puede pagar con
     * creditos (premium, o sin {@code precioCreditos} positivo).
     */
    public static Optional<PrecioEnCreditos> enCreditos(ProductoDelCatalogo producto, Instant ahora) {
        Objects.requireNonNull(producto, "producto");
        if (!producto.tienePrecioEnCreditos()) {
            return Optional.empty();
        }
        Integer porcentaje = producto.porcentajeVigenteEn(ahora);
        try {
            return Optional.of(new PrecioEnCreditos(producto.precioCreditos(),
                    calcularEnCreditos(producto.precioCreditos(), porcentaje), porcentaje));
        } catch (IllegalArgumentException datoRaro) {
            // Un porcentaje fuera de 1..99 ya lo descarta la promocion; esto es
            // el seguro de que un dato raro del catalogo no se cobre a ciegas.
            return Optional.empty();
        }
    }

    private static BigDecimal alMinimo(BigDecimal importe, Moneda moneda) {
        BigDecimal minimo = BigDecimal.ONE.movePointLeft(moneda.escala());
        return importe.compareTo(minimo) < 0 ? minimo : importe;
    }
}
