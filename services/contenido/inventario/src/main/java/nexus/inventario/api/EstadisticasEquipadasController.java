package nexus.inventario.api;

import nexus.inventario.aplicacion.ConsultarEstadisticasEquipadas;
import nexus.inventario.configuracion.IdentidadDelLlamador;
import nexus.inventario.dominio.EstadisticasHeroe;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Estadisticas equipadas de un heroe; el propietario lo resuelve {@link IdentidadDelLlamador}. */
@RestController
@RequestMapping("/api/v1/inventario/heroes/{heroeId}/estadisticas")
public class EstadisticasEquipadasController {

    private static final String CABECERA_IDENTIDAD = IdentidadDelLlamador.CABECERA_PROPIETARIO;
    private final ConsultarEstadisticasEquipadas consulta;
    private final IdentidadDelLlamador identidad;

    public EstadisticasEquipadasController(ConsultarEstadisticasEquipadas consulta, IdentidadDelLlamador identidad) {
        this.consulta = consulta;
        this.identidad = identidad;
    }

    @GetMapping
    public EstadisticasEquipadasResponse consultar(
            Authentication autenticacion,
            @RequestHeader(name = CABECERA_IDENTIDAD, required = false) String cabecera,
            @PathVariable String heroeId) {
        EstadisticasHeroe estadisticas =
                consulta.consultar(identidad.propietario(autenticacion, cabecera), heroeId);
        return EstadisticasEquipadasResponse.de(heroeId, estadisticas);
    }
}
