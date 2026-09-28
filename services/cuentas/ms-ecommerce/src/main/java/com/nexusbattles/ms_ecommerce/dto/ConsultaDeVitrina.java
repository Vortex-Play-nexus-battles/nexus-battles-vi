package com.nexusbattles.ms_ecommerce.dto;

import com.nexusbattles.ms_ecommerce.precios.Moneda;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Lo que se pide a la vitrina (GET /vitrina, contrato 1.4.0).
 *
 * @param numero        pagina, desde 0
 * @param tamano        productos por pagina
 * @param tipo          solo ese tipo; null o en blanco, todos
 * @param moneda        la de los precios de la respuesta
 * @param precioMinimo  precio final minimo en esa moneda; null sin limite
 * @param precioMaximo  precio final maximo en esa moneda; null sin limite
 * @param enPromocion   true = solo con promocion vigente
 * @param busqueda      texto libre; null o en blanco, sin buscar
 */
public record ConsultaDeVitrina(int numero, int tamano, String tipo, Moneda moneda, BigDecimal precioMinimo,
                                BigDecimal precioMaximo, boolean enPromocion, String busqueda) {

    public ConsultaDeVitrina {
        Objects.requireNonNull(moneda, "moneda");
    }

    /** La consulta de antes de 1.4.0: una pagina, quizas un tipo, en COP y sin filtros. */
    public static ConsultaDeVitrina de(int numero, int tamano, String tipo) {
        return new ConsultaDeVitrina(numero, tamano, tipo, Moneda.COP, null, null, false, null);
    }
}
