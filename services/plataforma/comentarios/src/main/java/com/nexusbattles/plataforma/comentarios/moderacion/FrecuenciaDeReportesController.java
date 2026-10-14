package com.nexusbattles.plataforma.comentarios.moderacion;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/comentarios/moderacion/reportes/frecuencia} — la
 * frecuencia de reportes de un periodo (§7.3.8, comentarios.yaml 1.15.0).
 * Misma guarda que el resto de la moderacion (Moderador, Administrador, Super
 * Administrador), puesta por {@code SecurityConfig} sobre el prefijo. Dos
 * segmentos literales: no la confunde el patron {@code /{commentId}} del
 * detalle.
 */
@RestController
@RequestMapping("/api/v1/comentarios/moderacion/reportes/frecuencia")
public class FrecuenciaDeReportesController {

    private final FrecuenciaDeReportes frecuencia;

    public FrecuenciaDeReportesController(FrecuenciaDeReportes frecuencia) {
        this.frecuencia = frecuencia;
    }

    @GetMapping
    public FrecuenciaResponse consultar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta) {
        return FrecuenciaResponse.desde(frecuencia.consultar(desde, hasta));
    }

    /** {@code FrecuenciaDeReportes} del contrato. */
    public record FrecuenciaResponse(String desde, String hasta, long total, List<PorDiaResponse> porDia,
            Map<String, Long> porCategoria, long comentariosReportados, long reportantes) {

        static FrecuenciaResponse desde(FrecuenciaDeReportes.Frecuencia f) {
            Map<String, Long> categorias = new LinkedHashMap<>();
            f.porCategoria().forEach((categoria, cuantos) -> categorias.put(categoria.name(), cuantos));
            return new FrecuenciaResponse(f.desde().toString(), f.hasta().toString(), f.total(),
                    f.porDia().stream().map(d -> new PorDiaResponse(d.fecha().toString(), d.reportes())).toList(),
                    categorias, f.comentariosReportados(), f.reportantes());
        }
    }

    public record PorDiaResponse(String fecha, long reportes) {
    }
}
