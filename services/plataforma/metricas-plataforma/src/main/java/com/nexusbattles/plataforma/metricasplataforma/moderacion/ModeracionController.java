package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;

/** {@code GET /api/v1/moderacion?desde&hasta} — HU-MET-001. Sin periodo: los ultimos 30 dias. */
@RestController
@RequestMapping("/api/v1/moderacion")
public class ModeracionController {

    private final FuenteDeModeracion fuente;
    private final Integer umbralSancionesPorDia;
    private final Clock reloj;

    public ModeracionController(FuenteDeModeracion fuente,
                                @Value("${metricas.moderacion.umbral-sanciones-por-dia:#{null}}") Integer umbralSancionesPorDia,
                                Clock reloj) {
        this.fuente = fuente;
        this.umbralSancionesPorDia = umbralSancionesPorDia;
        this.reloj = reloj;
    }

    @GetMapping
    public TableroDeModeracion tablero(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        OffsetDateTime fin = hasta == null ? OffsetDateTime.now(reloj) : hasta;
        OffsetDateTime inicio = desde == null ? fin.minusDays(30) : desde;
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException("el fin del periodo debe ser posterior a su inicio");
        }
        return TableroDeModeracion.de(fuente.consultar(inicio, fin), umbralSancionesPorDia);
    }

    @ExceptionHandler(FuenteDeModeracion.FuenteNoDisponible.class)
    public ProblemDetail fuenteCaida(FuenteDeModeracion.FuenteNoDisponible ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problema.setType(URI.create("https://nexusbattles.local/errores/fuente-no-disponible"));
        problema.setTitle("La fuente de moderacion no responde");
        return problema;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail periodoInvalido(IllegalArgumentException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problema.setType(URI.create("https://nexusbattles.local/errores/periodo-invalido"));
        problema.setTitle("Periodo invalido");
        return problema;
    }
}
