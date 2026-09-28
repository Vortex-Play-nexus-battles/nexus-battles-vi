package nexus.misiones.dominio;

import java.util.List;

/**
 * El jefe final (seccion 7.8.3). En el ejemplo del documento: «El Guardian
 * Eterno - Guerrero Tanque con 100 puntos de vida y habilidades potenciadas».
 *
 * @param vida    la del documento; nula = la del prototipo en el nivel del heroe
 * @param defensa nula = la del prototipo en el nivel del heroe
 */
public record Jefe(
        String nombre,
        String prototipo,
        Integer vida,
        Integer defensa,
        String descripcion,
        List<List<String>> rotaciones) {

    public Jefe {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El jefe necesita nombre.");
        }
        if (prototipo == null || prototipo.isBlank()) {
            throw new IllegalArgumentException("El jefe «" + nombre + "» necesita un prototipo del catalogo.");
        }
        rotaciones = rotaciones == null ? List.of() : rotaciones.stream().map(List::copyOf).toList();
    }
}
