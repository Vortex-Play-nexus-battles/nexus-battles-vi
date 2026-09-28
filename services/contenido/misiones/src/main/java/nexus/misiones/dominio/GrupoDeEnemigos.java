package nexus.misiones.dominio;

import java.util.List;

/**
 * Enemigos regulares de un tipo (seccion 7.8.3, «lista de adversarios
 * controlados por IA»): «Sombras Corrompidas (x10)».
 *
 * <p>El documento describe a los enemigos solo con palabras («ataque
 * moderado», «alta defensa»). Para que el motor de combate los resuelva hace
 * falta un prototipo del catalogo de heroes (su formula de ataque y su reparto
 * de efectos) y unas estadisticas: eso es {@code prototipo}, {@code vida} y
 * {@code defensa}, y en las misiones del documento va marcado como
 * PROVISIONAL en la semilla (decision del PO, seccion 7.8.13). Vida y defensa
 * nulas = las del prototipo en el nivel del heroe (vista por nivel de heroes).
 *
 * @param rotaciones estrategia predefinida de la IA del enemigo (7.8.6); vacia
 *                   = ataque basico, que es el comportamiento por defecto
 */
public record GrupoDeEnemigos(
        String nombre,
        int cantidad,
        String descripcion,
        String prototipo,
        Integer vida,
        Integer defensa,
        List<List<String>> rotaciones) {

    public GrupoDeEnemigos {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Un grupo de enemigos necesita nombre.");
        }
        if (cantidad < 1) {
            throw new IllegalArgumentException("«" + nombre + "» necesita al menos un enemigo.");
        }
        if (prototipo == null || prototipo.isBlank()) {
            throw new IllegalArgumentException(
                    "«" + nombre + "» necesita un prototipo del catalogo para que el motor lo resuelva.");
        }
        rotaciones = rotaciones == null ? List.of() : rotaciones.stream().map(List::copyOf).toList();
    }
}
