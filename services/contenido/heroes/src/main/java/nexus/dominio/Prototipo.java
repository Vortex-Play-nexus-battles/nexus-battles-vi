package nexus.dominio;

import java.util.List;

/**
 * Prototipo de heroe de la Tabla 5 (seccion 6.1.1, p. 25), con sus estadisticas
 * de nivel 1 (Tabla 6) y sus tres acciones especiales (Tabla 7).
 */
public record Prototipo(
        String nombre,
        String tipo,
        String descripcion,
        boolean esSanador,
        Estadisticas estadisticasNivel1,
        List<Accion> acciones) {

    /** "Los heroes cuentan con tres acciones especiales" (seccion 6.1.2, p. 28). */
    public static final int ACCIONES_POR_HEROE = 3;

    public Prototipo {
        if (acciones == null || acciones.size() != ACCIONES_POR_HEROE) {
            throw new IllegalArgumentException(
                    "Todo héroe tiene exactamente tres acciones especiales (Tabla 7).");
        }
        acciones = List.copyOf(acciones);
    }
}
