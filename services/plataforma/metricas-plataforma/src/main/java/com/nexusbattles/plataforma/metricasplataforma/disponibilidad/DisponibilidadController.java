package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estado de disponibilidad del bloque e informe del periodo (HU-DIS-001).
 *
 * <p>Contrato: contracts/openapi/metricas-plataforma.yaml
 *
 * <ul>
 *   <li>{@code GET /api/v1/disponibilidad} — CP-01: el 100 % de los servicios
 *       desplegados reporta su disponibilidad.
 *   <li>{@code GET /api/v1/disponibilidad/informe} — CP-02: tiempo disponible e
 *       interrupciones registradas del periodo, listo para anexar al informe
 *       de avance.
 * </ul>
 *
 * <p>Los errores salen en problem details, igual que en los demas modulos
 * (regla 4 de plataforma).
 */
@RestController
@RequestMapping("/api/v1/disponibilidad")
public class DisponibilidadController {

    private final MonitorDeDisponibilidad monitor;

    public DisponibilidadController(MonitorDeDisponibilidad monitor) {
        this.monitor = monitor;
    }

    @GetMapping
    public EstadoResponse estado() {
        List<ServicioResponse> servicios = monitor.estadoActual().stream()
                .map(comprobacion -> new ServicioResponse(
                        comprobacion.servicio(),
                        comprobacion.disponible() ? "DISPONIBLE" : "INDISPONIBLE",
                        comprobacion.instante(),
                        comprobacion.detalle()))
                .toList();

        return new EstadoResponse(servicios);
    }

    /**
     * Informe del periodo. Sin fechas devuelve los ultimos treinta dias, que es
     * el periodo «mensual» que fija DEC-01.
     */
    @GetMapping("/informe")
    public InformeResponse informe(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta) {

        InformeDeDisponibilidad informe =
                (desde == null || hasta == null) ? monitor.informeMensual() : monitor.informe(desde, hasta);

        List<LineaResponse> lineas = informe.servicios().stream()
                .map(linea -> new LineaResponse(
                        linea.servicio(),
                        linea.disponible().toMinutes(),
                        linea.indisponible().toMinutes(),
                        linea.porcentaje(),
                        linea.cumple(informe.umbral()),
                        linea.interrupciones().stream()
                                .map(interrupcion -> new InterrupcionResponse(
                                        interrupcion.inicio(),
                                        interrupcion.fin(),
                                        interrupcion.detalle()))
                                .toList()))
                .toList();

        return new InformeResponse(
                informe.desde(),
                informe.hasta(),
                informe.periodo().toMinutes(),
                informe.umbral(),
                informe.porcentajeDelBloque(),
                informe.cumpleElUmbral(),
                lineas);
    }

    /** Un periodo al reves es un error del cliente, no un fallo del servicio. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail periodoInvalido(IllegalArgumentException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problema.setTitle("Periodo invalido");
        problema.setType(java.net.URI.create("https://nexusbattles.local/errores/periodo-invalido"));
        return problema;
    }

    public record EstadoResponse(List<ServicioResponse> servicios) {}

    public record ServicioResponse(String servicio, String estado, Instant comprobadoEn, String detalle) {}

    public record InformeResponse(
            Instant desde,
            Instant hasta,
            long periodoMinutos,
            double umbral,
            double porcentajeDelBloque,
            boolean cumpleElUmbral,
            List<LineaResponse> servicios) {}

    public record LineaResponse(
            String servicio,
            long disponibleMinutos,
            long indisponibleMinutos,
            double porcentaje,
            boolean cumple,
            List<InterrupcionResponse> interrupciones) {}

    public record InterrupcionResponse(Instant inicio, Instant fin, String detalle) {}
}
