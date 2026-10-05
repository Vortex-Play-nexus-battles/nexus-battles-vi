package com.nexusbattles.plataforma.correo.api;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Fechas para personas: el destinatario de un correo no lee un ISO-8601 crudo.
 *
 * <p>Se conserva el huso con el que llego la fecha (ver
 * {@code adjust-dates-to-context-time-zone} en application.yml): un aviso de
 * acceso o una suspension que mostrara la hora en otro huso haria dudar al
 * jugador de si fue el, o de cuando termina su sancion.
 */
final class FechaLegible {

    private static final DateTimeFormatter LEGIBLE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm (OOOO)", Locale.of("es"));

    private FechaLegible() {}

    /** p. ej. {@code 21/09/2026 a las 15:00 (GMT-05:00)}; nulo si no hay fecha. */
    static String de(OffsetDateTime fecha) {
        return fecha == null ? null : fecha.format(LEGIBLE);
    }
}
