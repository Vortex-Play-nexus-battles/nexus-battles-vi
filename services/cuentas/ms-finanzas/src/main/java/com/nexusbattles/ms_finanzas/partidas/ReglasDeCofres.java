package com.nexusbattles.ms_finanzas.partidas;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.Locale;
import java.util.Objects;

/**
 * Las reglas del cofre — §7.6 del documento y cofres.yaml 1.1.0 (B7).
 *
 * <p><b>Del documento, inalterables</b> (Project Charter: los valores numéricos
 * no son parámetros de ajuste): veinte créditos ganados por cofre
 * ({@link #CUOTA}) y dos cofres por semana como máximo
 * ({@link #MAXIMO_POR_SEMANA}).
 *
 * <p><b>Lo que el documento no fija, configurable</b> (con su decisión del PO):
 * la zona horaria en la que se cuenta la semana ISO (D-B7-17,
 * {@code FINANZAS_COFRES_ZONA}, {@code America/Bogota}), si el sobrante de la
 * cuota se conserva (D-B7-17, {@code FINANZAS_COFRES_CONSERVAR_SOBRANTE}, sí) y
 * la tabla del contenido (D-B7-18, provisional de desarrollo).
 *
 * @param zona                zona horaria de la semana ISO
 * @param conservarSobrante   si lo que pasa de la cuota cuenta para el siguiente cofre
 * @param tabla               qué puede traer un cofre
 * @param esperaMaximaMinutos tope de espera entre reintentos de una entrega fallida
 */
public record ReglasDeCofres(ZoneId zona, boolean conservarSobrante, TablaDeCofre tabla,
                             int esperaMaximaMinutos) {

    /** «veinte (20) créditos en juegos ganados» (§7.6). */
    public static final int CUOTA = 20;

    /** «Este beneficio solo podrá obtenerse dos veces por semana» (§7.6). */
    public static final int MAXIMO_POR_SEMANA = 2;

    public ReglasDeCofres {
        Objects.requireNonNull(zona, "La semana del cofre se cuenta en una zona horaria explícita.");
        Objects.requireNonNull(tabla, "Hace falta la tabla del contenido del cofre.");
        if (esperaMaximaMinutos < 1) {
            throw new IllegalArgumentException("La espera máxima entre reintentos es de al menos un minuto.");
        }
    }

    /**
     * La semana ISO 8601 (de lunes a domingo) de ese instante en la zona del
     * cofre: {@code 2026-W39}. El año es el de la semana, no el del calendario
     * (el 31 de diciembre de 2029 es de la 2030-W01).
     */
    public String semanaIso(Instant instante) {
        ZonedDateTime enLaZona = instante.atZone(zona);
        return String.format(Locale.ROOT, "%d-W%02d", enLaZona.get(IsoFields.WEEK_BASED_YEAR),
                enLaZona.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }
}
