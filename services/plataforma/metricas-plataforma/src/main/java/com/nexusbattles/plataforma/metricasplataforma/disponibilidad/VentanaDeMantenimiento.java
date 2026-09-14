package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import java.time.Instant;

/**
 * Ventana de mantenimiento programado (HU-DIS-001, DEC-01).
 *
 * <p>El tiempo que cae dentro de una ventana no cuenta como indisponibilidad:
 * la decision del Product Owner mide el 99,95 % «excluyendo las ventanas de
 * mantenimiento programado». Sin esta exclusion, una actualizacion planeada
 * hundiria la cifra del informe y el numero dejaria de significar lo que dice.
 *
 * @param inicio momento en que empieza el mantenimiento, inclusive
 * @param fin momento en que termina, exclusivo
 * @param motivo texto para el informe; no se interpreta
 */
public record VentanaDeMantenimiento(Instant inicio, Instant fin, String motivo) {

    public VentanaDeMantenimiento {
        if (inicio == null || fin == null) {
            throw new IllegalArgumentException("una ventana de mantenimiento necesita inicio y fin");
        }
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException("el fin de la ventana debe ser posterior a su inicio");
        }
    }

    /** True si el instante cae dentro de la ventana (inicio inclusive, fin exclusivo). */
    public boolean contiene(Instant instante) {
        return !instante.isBefore(inicio) && instante.isBefore(fin);
    }
}
