package com.nexusbattles.ms_ecommerce.precios;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * La moneda en que se va a expresar un precio y la tasa con la que se llega a
 * ella desde COP.
 *
 * <p>Se obtiene una vez por peticion ({@link TasasDeCambio#tarifa(Moneda)}) y
 * se aplica a todos los productos de esa respuesta: dos lineas del mismo
 * carrito nunca se convierten con tasas distintas aunque admin-parametros
 * cambie el valor a mitad de la peticion.
 *
 * @param moneda            la moneda de destino
 * @param copPorUnidad      pesos colombianos por una unidad de {@code moneda};
 *                          1 en COP
 */
public record Tarifa(Moneda moneda, BigDecimal copPorUnidad) {

    public Tarifa {
        Objects.requireNonNull(moneda, "moneda");
        Objects.requireNonNull(copPorUnidad, "copPorUnidad");
        if (copPorUnidad.signum() <= 0) {
            throw new IllegalArgumentException("La tasa de " + moneda + " tiene que ser mayor que cero");
        }
    }

    /** La tarifa de la moneda del catalogo: sin conversion. */
    public static Tarifa enPesos() {
        return new Tarifa(Moneda.COP, BigDecimal.ONE);
    }

    /** La tasa para guardar en una orden: null en COP, donde no hubo conversion. */
    public BigDecimal tasaParaMostrar() {
        return moneda.esLaDelCatalogo() ? null : copPorUnidad;
    }
}
