package com.nexusbattles.ms_chatbot.chat.catalogo;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Lo que dice el chat sobre el catálogo (1.10.0), en español y en inglés. Los
 * nombres de los productos van tal cual: son los de la tienda.
 */
final class TextosDelCatalogo {

    private static final Map<String, String[]> TIPOS = Map.of(
        "HEROE", new String[] {"héroe", "héroes", "hero", "heroes"},
        "HABILIDAD", new String[] {"habilidad", "habilidades", "ability", "abilities"},
        "ARMA", new String[] {"arma", "armas", "weapon", "weapons"},
        "ARMADURA", new String[] {"armadura", "armaduras", "armor piece", "armor pieces"},
        "ITEM", new String[] {"ítem", "ítems", "item", "items"},
        "EPICA", new String[] {"épica", "épicas", "epic", "epics"});

    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");

    private final boolean ingles;

    private TextosDelCatalogo(boolean ingles) {
        this.ingles = ingles;
    }

    static TextosDelCatalogo en(boolean ingles) {
        return new TextosDelCatalogo(ingles);
    }

    boolean ingles() {
        return ingles;
    }

    private String elegir(String espanol, String english) {
        return ingles ? english : espanol;
    }

    String tipo(String tipo, boolean plural) {
        String[] nombres = TIPOS.get(tipo);
        if (nombres == null) {
            return elegir(plural ? "productos" : "producto", plural ? "products" : "product");
        }
        return nombres[(ingles ? 2 : 0) + (plural ? 1 : 0)];
    }

    String precio(ProductoDelCatalogoDto producto) {
        String texto;
        if (producto.esPremium() && producto.precioMonedaReal() != null) {
            texto = pesos(producto.precioMonedaReal()) + elegir(" COP, solo con dinero real", " COP, real money only");
        } else if (producto.precioCreditos() != null) {
            texto = producto.precioCreditos() + elegir(" créditos", " credits");
        } else {
            texto = elegir("sin precio publicado", "no published price");
        }
        if (producto.promocion() != null && producto.promocion().porcentaje() != null) {
            texto += elegir(", con " + producto.promocion().porcentaje() + " % de descuento",
                ", " + producto.promocion().porcentaje() + "% off");
        }
        return texto;
    }

    private static String pesos(BigDecimal valor) {
        NumberFormat formato = NumberFormat.getNumberInstance(COLOMBIA);
        formato.setMaximumFractionDigits(2);
        return formato.format(valor);
    }

    String conPrecio(ProductoDelCatalogoDto producto) {
        return "«" + producto.nombre() + "» (" + tipo(producto.tipo(), false) + "): " + precio(producto);
    }

    String unPrecio(ProductoDelCatalogoDto producto) {
        return elegir("«" + producto.nombre() + "» (" + tipo(producto.tipo(), false) + ") cuesta " + precio(producto)
                + ". Lo encuentras en la «Tienda».",
            "«" + producto.nombre() + "» (" + tipo(producto.tipo(), false) + ") costs " + precio(producto)
                + ". You'll find it in the «Tienda» (Store).");
    }

    String variosPrecios(int total, String lista) {
        return elegir("Encontré " + total + " productos con ese nombre: " + lista + ". Están en la «Tienda».",
            "I found " + total + " products with that name: " + lista + ". They're in the «Tienda» (Store).");
    }

    String demasiados(int total, String lista) {
        return elegir("Hay " + total + " productos que se parecen a eso, por ejemplo: " + lista
                + ". Dime el nombre completo y te digo su precio.",
            "There are " + total + " products that look like that, for example: " + lista
                + ". Tell me the full name and I'll tell you its price.");
    }

    String ficha(ProductoDelCatalogoDto producto) {
        String descripcion = producto.descripcion() == null || producto.descripcion().isBlank()
            ? "" : " " + producto.descripcion().strip();
        if (!descripcion.isEmpty() && !descripcion.endsWith(".")) {
            descripcion += ".";
        }
        return elegir("«" + producto.nombre() + "» es " + articulo(producto.tipo()) + " " + tipo(producto.tipo(), false)
                + "." + descripcion + " Precio: " + precio(producto) + ". Su ficha completa está en la «Tienda».",
            "«" + producto.nombre() + "» is " + articuloEn(producto.tipo()) + " " + tipo(producto.tipo(), false) + "."
                + descripcion + " Price: " + precio(producto) + ". Its full details are in the «Tienda» (Store).");
    }

    // «un arma» (femenino con a tónica), «una armadura», «una épica».
    private static String articulo(String tipo) {
        return "HABILIDAD".equals(tipo) || "ARMADURA".equals(tipo) || "EPICA".equals(tipo) ? "una" : "un";
    }

    private static String articuloEn(String tipo) {
        return "ITEM".equals(tipo) || "EPICA".equals(tipo) || "ARMADURA".equals(tipo) || "HABILIDAD".equals(tipo)
            ? "an" : "a";
    }

    String listado(long total, String porTipo, String ejemplos) {
        return elegir("En el catálogo hay " + total + " productos a la venta (" + porTipo + "). Algunos: " + ejemplos
                + ". Los ves todos, con su ficha y su precio, en la «Tienda».",
            "The catalog has " + total + " products for sale (" + porTipo + "). Some of them: " + ejemplos
                + ". You can see them all, with details and prices, in the «Tienda» (Store).");
    }

    String listadoDeTipo(long total, String tipo, String ejemplos) {
        return elegir("Hay " + total + " " + tipo + " a la venta, por ejemplo: " + ejemplos
                + ". En la «Tienda» está la lista completa.",
            "There are " + total + " " + tipo + " for sale, for example: " + ejemplos
                + ". You can see them all in the «Tienda» (Store).");
    }

    String ningunoDeTipo(String tipo) {
        return elegir("Ahora mismo no hay " + tipo + " a la venta en la «Tienda».",
            "There are no " + tipo + " for sale in the «Tienda» (Store) right now.");
    }

    String catalogoVacio() {
        return elegir("Ahora mismo no hay productos a la venta en la «Tienda».",
            "There are no products for sale in the «Tienda» (Store) right now.");
    }

    String noDisponible() {
        return elegir("No pude consultar el catálogo en este momento: el servicio no respondió a tiempo. "
                + "Inténtalo de nuevo en unos minutos o mira la «Tienda».",
            "I couldn't check the catalog right now: the service didn't answer in time. "
                + "Please try again in a few minutes or look at the «Tienda» (Store).");
    }
}
