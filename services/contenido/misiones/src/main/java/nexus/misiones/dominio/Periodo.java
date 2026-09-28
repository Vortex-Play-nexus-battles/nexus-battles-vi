package nexus.misiones.dominio;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

/**
 * Periodo de renovacion de los intentos de un desafio (seccion 7.8.2:
 * «limite de intentos diarios o semanales»). Los periodos se cuentan en UTC y
 * la semana empieza el lunes: el documento no fija la hora de corte, y una
 * sola referencia evita que dos instancias del servicio cuenten distinto.
 */
public enum Periodo {
    DIARIO,
    SEMANAL;

    /** Instante en que empezo el periodo que contiene a {@code ahora}. */
    public Instant inicio(Instant ahora) {
        LocalDate dia = ahora.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate inicio = this == DIARIO
                ? dia
                : dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return inicio.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
