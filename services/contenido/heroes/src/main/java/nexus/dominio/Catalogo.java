package nexus.dominio;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Catalogo de prototipos de heroe. No fija la cantidad: los ocho iniciales
 * son datos, no un limite (acta 2026-08-13). Este catalogo es el consumidor
 * del contenido: la administracion (crear/modificar productos tipo heroe)
 * pertenece al modulo de productos, seccion 7.2.
 */
public class Catalogo {

    private final List<Prototipo> prototipos = new ArrayList<>();

    public static Catalogo conPrototiposIniciales() {
        Catalogo catalogo = new Catalogo();
        PrototiposIniciales.LISTA.forEach(catalogo::registrar);
        return catalogo;
    }

    public void registrar(Prototipo prototipo) {
        boolean yaExiste = prototipos.stream()
                .anyMatch(p -> normalizar(p.nombre()).equals(normalizar(prototipo.nombre())));
        if (yaExiste) {
            throw new IllegalArgumentException(
                    "Ya existe un prototipo con el nombre \"" + prototipo.nombre() + "\".");
        }
        prototipos.add(prototipo);
    }

    public List<Prototipo> listar() {
        return List.copyOf(prototipos);
    }

    public Prototipo fichaDe(String nombre) {
        return prototipos.stream()
                .filter(p -> normalizar(p.nombre()).equals(normalizar(nombre)))
                .findFirst()
                .orElseThrow(HeroeNoDisponibleException::new);
    }

    /** Busqueda tolerante a tildes y mayusculas; los datos conservan su forma exacta. */
    private static String normalizar(String nombre) {
        return Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
