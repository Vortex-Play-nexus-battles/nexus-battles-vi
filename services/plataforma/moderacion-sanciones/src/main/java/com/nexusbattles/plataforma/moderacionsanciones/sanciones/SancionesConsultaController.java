package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code GET /sanciones/usuarios/{uid}/activa} y {@code GET /sanciones/metricas}
 * — moderacion-sanciones-consulta.yaml 1.2.0.
 */
@RestController
@RequestMapping("/api/v1/sanciones")
public class SancionesConsultaController {

    private final ConsultaSancionActivaService service;
    private final SancionesService sanciones;
    private final Clock reloj;

    public SancionesConsultaController(ConsultaSancionActivaService service, SancionesService sanciones, Clock reloj) {
        this.service = service;
        this.sanciones = sanciones;
        this.reloj = reloj;
    }

    /**
     * Agregados de moderacion de un periodo (HU-MET-001). Solo cuentas, sin
     * identificadores: por eso vive junto a la consulta publica y no en el
     * contrato de administracion. Sin {@code desde}/{@code hasta}: los ultimos
     * 30 dias.
     */
    @GetMapping("/metricas")
    public MetricasDeModeracion metricas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        OffsetDateTime fin = hasta == null ? OffsetDateTime.now(reloj) : hasta;
        OffsetDateTime inicio = desde == null ? fin.minusDays(30) : desde;
        return sanciones.metricas(inicio, fin);
    }

    @GetMapping("/usuarios/{usuarioId}/activa")
    public SancionActivaResponse consultarActiva(@PathVariable UUID usuarioId) {
        var resultado = service.consultar(usuarioId);
        return new SancionActivaResponse(resultado.sancionActiva(), resultado.motivo(), resultado.vigenteHasta(),
                resultado.tipo());
    }

    public record SancionActivaResponse(boolean sancionActiva, String motivo, OffsetDateTime vigenteHasta,
                                        String tipo) {
    }
}
