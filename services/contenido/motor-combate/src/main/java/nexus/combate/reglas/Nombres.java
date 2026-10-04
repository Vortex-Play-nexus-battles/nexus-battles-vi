package nexus.combate.reglas;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Comparacion de nombres del documento sin tildes ni mayusculas, con el mismo
 * criterio que el catalogo de heroes: «Agonía» y «agonia» son la misma accion.
 */
public final class Nombres {

    private Nombres() {
    }

    public static String normalizar(String nombre) {
        if (nombre == null) {
            return "";
        }
        return Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Codigo estable de un nombre: «Cono de hielo» -> {@code CONO_DE_HIELO}. */
    public static String codigo(String nombre) {
        return normalizar(nombre).toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}
