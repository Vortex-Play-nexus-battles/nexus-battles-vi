package com.nexusbattles.plataforma.observabilidad;

import java.util.Locale;
import java.util.Set;

/**
 * Lectura o escritura (HU-REN-002).
 *
 * <p>La restriccion de esa historia lo exige textualmente: «los datos
 * capturados por la herramienta de instrumentacion deben etiquetarse
 * correctamente para diferenciar entre consultas de lectura —listados— y
 * operaciones de escritura —pujas—».
 *
 * <p>Se clasifica por el <b>metodo HTTP</b>, no por la ruta. Es la unica regla
 * que funciona igual en los veinte modulos sin que nadie tenga que mantener
 * una lista de rutas que se queda vieja al dia siguiente.
 */
public enum TipoDeOperacion {

    /** Consulta: no cambia estado. Listados, fichas, busquedas. */
    LECTURA,

    /** Modificacion: puja, creacion, borrado. */
    ESCRITURA,

    /**
     * Ni una cosa ni otra.
     *
     * <p>{@code OPTIONS} de CORS, {@code TRACE}, o un metodo que no
     * reconocemos. No se fuerza a lectura para no ensuciar el percentil de
     * lecturas con preflight, que no es trafico de jugador.
     */
    OTRA;

    private static final Set<String> METODOS_DE_LECTURA = Set.of("GET", "HEAD");

    private static final Set<String> METODOS_DE_ESCRITURA =
            Set.of("POST", "PUT", "PATCH", "DELETE");

    public static TipoDeOperacion deMetodo(String metodo) {
        if (metodo == null || metodo.isBlank()) {
            return OTRA;
        }
        String normalizado = metodo.trim().toUpperCase(Locale.ROOT);
        if (METODOS_DE_LECTURA.contains(normalizado)) {
            return LECTURA;
        }
        if (METODOS_DE_ESCRITURA.contains(normalizado)) {
            return ESCRITURA;
        }
        return OTRA;
    }

    /** Nombre para el informe y para el panel. */
    public String etiqueta() {
        return switch (this) {
            case LECTURA -> "lectura";
            case ESCRITURA -> "escritura";
            case OTRA -> "otra";
        };
    }
}
