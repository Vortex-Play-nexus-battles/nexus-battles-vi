package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * {@code GET /api/v1/moderacion?desde&hasta} — HU-MET-001. Sin periodo: los ultimos 30 dias.
 *
 * <p>Las sanciones son obligatorias (sin ellas, 503). Las cuentas de ms-identidad y la frecuencia de reportes de
 * comentarios son accesorias: si una no se puede leer, el tablero sale igual y lo dice (CA-03, RF-MET-001). A las dos
 * se les reenvia el token de quien mira el tablero: son rutas de personas, no de servicios.
 */
@RestController
@RequestMapping("/api/v1/moderacion")
public class ModeracionController {

    private final FuenteDeModeracion fuente;
    private final FuenteDeUsuarios usuarios;
    private final FuenteDeReportes reportes;
    private final UmbralesDeModeracion umbrales;
    private final Clock reloj;

    public ModeracionController(FuenteDeModeracion fuente, FuenteDeUsuarios usuarios, FuenteDeReportes reportes,
                                UmbralesDeModeracion umbrales, Clock reloj) {
        this.fuente = fuente;
        this.usuarios = usuarios;
        this.reportes = reportes;
        this.umbrales = umbrales;
        this.reloj = reloj;
    }

    @GetMapping
    public TableroDeModeracion tablero(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorizacion) {
        OffsetDateTime fin = hasta == null ? OffsetDateTime.now(reloj) : hasta;
        OffsetDateTime inicio = desde == null ? fin.minusDays(30) : desde;
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException("el fin del periodo debe ser posterior a su inicio");
        }
        FuenteDeModeracion.Agregados sanciones = fuente.consultar(inicio, fin);
        return TableroDeModeracion.de(sanciones, umbrales.vigentes(), cuentas(inicio, fin, autorizacion),
                frecuencia(inicio, fin, autorizacion));
    }

    private TableroDeModeracion.Lectura<FuenteDeUsuarios.Indicadores> cuentas(OffsetDateTime inicio,
                                                                            OffsetDateTime fin, String autorizacion) {
        try {
            return TableroDeModeracion.Lectura.de(usuarios.consultar(
                    inicio.toLocalDate().toString(), fin.toLocalDate().toString(), autorizacion));
        } catch (FuenteDeUsuarios.NoDisponible sinUsuarios) {
            return TableroDeModeracion.Lectura.fallida(sinUsuarios.causa(), sinUsuarios.getMessage());
        }
    }

    private TableroDeModeracion.Lectura<FuenteDeReportes.Frecuencia> frecuencia(OffsetDateTime inicio,
                                                                              OffsetDateTime fin, String autorizacion) {
        try {
            return TableroDeModeracion.Lectura.de(reportes.consultar(inicio, fin, autorizacion));
        } catch (FuenteDeReportes.NoDisponible sinReportes) {
            return TableroDeModeracion.Lectura.fallida(sinReportes.causa(), sinReportes.getMessage());
        }
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
