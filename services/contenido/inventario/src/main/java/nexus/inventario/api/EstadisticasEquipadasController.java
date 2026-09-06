package nexus.inventario.api;

import nexus.inventario.aplicacion.ConsultarEstadisticasEquipadas;
import nexus.inventario.dominio.EstadisticasHeroe;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventario/heroes/{heroeId}/estadisticas")
public class EstadisticasEquipadasController {

    private static final String CABECERA_IDENTIDAD = "X-User-Name";
    private final ConsultarEstadisticasEquipadas consulta;

    public EstadisticasEquipadasController(ConsultarEstadisticasEquipadas consulta) {
        this.consulta = consulta;
    }

    @GetMapping
    public EstadisticasEquipadasResponse consultar(
            @RequestHeader(name = CABECERA_IDENTIDAD, required = false) String identidad,
            @PathVariable String heroeId) {
        EstadisticasHeroe estadisticas = consulta.consultar(identidad, heroeId);
        return EstadisticasEquipadasResponse.de(heroeId, estadisticas);
    }
}
