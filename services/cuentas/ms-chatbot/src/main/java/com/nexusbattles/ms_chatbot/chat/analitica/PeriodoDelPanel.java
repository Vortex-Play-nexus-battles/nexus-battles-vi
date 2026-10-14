package com.nexusbattles.ms_chatbot.chat.analitica;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

// HU-CHA-012: el periodo de lo que se consulta en el panel (las analiticas y,
// desde ms-chatbot.yaml 1.7.0, las preguntas sin respuesta): dias completos
// en hora de Colombia, 'desde' y 'hasta' inclusive (formato ISO,
// 2026-09-30). Sin fechas, los ultimos 30 dias terminando hoy. Como mucho
// un ano: un periodo mas largo haria cargar demasiados registros en memoria
// para la tendencia diaria, y un ano cubre el uso real de un tablero.
public record PeriodoDelPanel(LocalDate desde, LocalDate hasta) {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");
    public static final int DIAS_POR_DEFECTO = 30;
    public static final int MAXIMO_DIAS = 366;

    /** Completa las fechas que faltan (los ultimos 30 dias); no valida. */
    public static PeriodoDelPanel resolver(LocalDate desde, LocalDate hasta) {
        LocalDate fin = hasta != null ? hasta : LocalDate.now(ZONA);
        LocalDate inicio = desde != null ? desde : fin.minusDays(DIAS_POR_DEFECTO - 1L);
        return new PeriodoDelPanel(inicio, fin);
    }

    /** 400 si falta una fecha, si 'hasta' es anterior a 'desde' o si pasa de un ano. */
    public static void validar(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El periodo necesita fecha 'desde' y 'hasta'.");
        }
        if (hasta.isBefore(desde)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha 'hasta' no puede ser anterior a 'desde'.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El periodo no puede superar " + MAXIMO_DIAS + " dias.");
        }
    }

    /** Medianoche de 'desde' en la zona del panel, inclusive. */
    public Instant inicio() {
        return desde.atStartOfDay(ZONA).toInstant();
    }

    /** Medianoche del dia siguiente a 'hasta' en la zona del panel, exclusivo. */
    public Instant fin() {
        return hasta.plusDays(1).atStartOfDay(ZONA).toInstant();
    }
}
