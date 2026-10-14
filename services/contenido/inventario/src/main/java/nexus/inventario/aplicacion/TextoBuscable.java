package nexus.inventario.aplicacion;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;

/**
 * Cómo se compara un texto en la búsqueda del inventario (HU-INV-002): sin
 * tildes, sin mayúsculas y con los espacios repetidos colapsados. «BRÚMA»,
 * «bruma» y «Bruma» son el mismo criterio.
 */
public final class TextoBuscable {

    /** Nombre legible de cada tipo, para que «arma» o «épica» encuentren lo suyo. */
    static final Map<TipoElementoInventario, String> NOMBRE_DEL_TIPO = Map.of(
            TipoElementoInventario.HEROE, "Héroe",
            TipoElementoInventario.HABILIDAD, "Habilidad",
            TipoElementoInventario.ARMA, "Arma",
            TipoElementoInventario.ARMADURA, "Armadura",
            TipoElementoInventario.ITEM, "Ítem",
            TipoElementoInventario.EPICA, "Épica");

    /** Nombre legible de cada parte de armadura. */
    static final Map<ParteArmadura, String> NOMBRE_DE_LA_PARTE = Map.of(
            ParteArmadura.CASCO, "Casco",
            ParteArmadura.PECHO, "Pecho",
            ParteArmadura.GUANTES, "Guantes",
            ParteArmadura.BRAZALETES, "Brazaletes",
            ParteArmadura.PANTALON, "Pantalón",
            ParteArmadura.ZAPATOS, "Zapatos");

    private TextoBuscable() {
    }

    public static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
