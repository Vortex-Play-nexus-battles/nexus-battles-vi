package com.nexusbattles.ms_ecommerce.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * La busqueda de la vitrina (7.5: «busqueda para cualquier informacion
 * presente en el producto de juego incluido el precio», que «permita
 * identificar en pocos caracteres los productos»).
 *
 * <p>Como compara una persona: sin tildes ni mayusculas, por fragmentos (tres
 * letras ya encuentran), y con TODAS las palabras de la consulta. Una palabra
 * hecha solo de cifras y separadores ({@code 45000}, {@code 45.000},
 * {@code $45 000}) tambien casa con el precio ya convertido a la moneda de la
 * pagina. Una palabra con letras no mira el precio: «espada 2» no trae todo lo
 * que cuesta algo con un 2.
 *
 * <p>El indice es el texto normalizado de cada producto (nombre, descripcion,
 * habilidades y tipo, que en el catalogo ya son palabras en espanol: HEROE,
 * ARMA, ITEM...); buscar es recorrerlo, que sobre el tamano de este catalogo
 * (cientos de productos, en una copia de 30 s) no se nota.
 */
final class BusquedaEnLaVitrina {

    private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}+");
    private static final Pattern SOLO_CIFRAS = Pattern.compile("[\\d\\s.,$]+");
    private static final Pattern NO_CIFRA = Pattern.compile("\\D");

    private final List<String> terminos;

    private BusquedaEnLaVitrina(List<String> terminos) {
        this.terminos = terminos;
    }

    /** La consulta, partida en palabras normalizadas; sin palabras, todo coincide. */
    static BusquedaEnLaVitrina de(String consulta) {
        if (consulta == null || consulta.isBlank()) {
            return new BusquedaEnLaVitrina(List.of());
        }
        return new BusquedaEnLaVitrina(Arrays.stream(consulta.strip().split("\\s+"))
                .filter(termino -> !termino.isBlank())
                .toList());
    }

    boolean vacia() {
        return terminos.isEmpty();
    }

    /**
     * @param texto  el texto normalizado del producto ({@link #normalizar})
     * @param precio el precio final en la moneda de la pagina
     */
    boolean coincide(String texto, BigDecimal precio) {
        for (String termino : terminos) {
            if (!coincideUnTermino(termino, texto, precio)) {
                return false;
            }
        }
        return true;
    }

    private static boolean coincideUnTermino(String termino, String texto, BigDecimal precio) {
        String normal = normalizar(termino);
        if (!normal.isEmpty() && texto.contains(normal)) {
            return true;
        }
        if (precio != null && SOLO_CIFRAS.matcher(termino).matches()) {
            String cifras = NO_CIFRA.matcher(termino).replaceAll("");
            String delPrecio = NO_CIFRA.matcher(precio.toPlainString()).replaceAll("");
            return !cifras.isEmpty() && delPrecio.contains(cifras);
        }
        return false;
    }

    /** Minusculas, sin tildes ni espacios sobrantes: «Épica» y «epica» son lo mismo. */
    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = MARCAS_DIACRITICAS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
        return sinTildes.toLowerCase(Locale.ROOT).strip();
    }
}
