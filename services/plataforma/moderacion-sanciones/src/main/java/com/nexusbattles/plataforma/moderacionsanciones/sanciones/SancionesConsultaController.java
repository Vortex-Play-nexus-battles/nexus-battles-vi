package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code GET /sanciones/usuarios/{uid}/activa} — moderacion-sanciones-consulta.yaml 1.1.0. */
@RestController
@RequestMapping("/api/v1/sanciones")
public class SancionesConsultaController {

    private final ConsultaSancionActivaService service;

    public SancionesConsultaController(ConsultaSancionActivaService service) {
        this.service = service;
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
