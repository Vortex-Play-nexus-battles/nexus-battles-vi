package com.nexusbattles.plataforma.metricasplataforma.sistema;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estado de los servicios para la pantalla «Sistema» de la consola
 * (metricas-plataforma.yaml, {@code GET /admin/sistema/servicios}).
 *
 * <p>Sin logica: el sondeo, sus plazos y la reutilizacion viven en
 * {@link EstadoDelSistema}.
 */
@RestController
@RequestMapping("/api/v1/admin/sistema")
public class SistemaController {

    private final EstadoDelSistema estado;

    public SistemaController(EstadoDelSistema estado) {
        this.estado = estado;
    }

    @GetMapping("/servicios")
    public RespuestaDelSistema servicios() {
        return estado.consultar();
    }
}
