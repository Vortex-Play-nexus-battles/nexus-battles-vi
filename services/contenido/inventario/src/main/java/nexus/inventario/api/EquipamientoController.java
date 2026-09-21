package nexus.inventario.api;

import nexus.inventario.aplicacion.GestionarEquipamiento;
import nexus.inventario.configuracion.IdentidadDelLlamador;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Equipamiento de un heroe; el propietario lo resuelve {@link IdentidadDelLlamador}. */
@RestController
@RequestMapping("/api/v1/inventario/heroes/{heroeId}/equipamiento")
public class EquipamientoController {

    private static final String CABECERA_IDENTIDAD = IdentidadDelLlamador.CABECERA_PROPIETARIO;
    private final GestionarEquipamiento gestion;
    private final IdentidadDelLlamador identidad;

    public EquipamientoController(GestionarEquipamiento gestion, IdentidadDelLlamador identidad) {
        this.gestion = gestion;
        this.identidad = identidad;
    }

    @GetMapping
    public EquipamientoResponse consultar(
            Authentication autenticacion,
            @RequestHeader(name = CABECERA_IDENTIDAD, required = false) String cabecera,
            @PathVariable String heroeId) {
        return EquipamientoResponse.de(
                gestion.consultar(identidad.propietario(autenticacion, cabecera), heroeId));
    }

    @PutMapping("/{elementoId}")
    public EquipamientoResponse equipar(
            Authentication autenticacion,
            @RequestHeader(name = CABECERA_IDENTIDAD, required = false) String cabecera,
            @PathVariable String heroeId,
            @PathVariable String elementoId) {
        return EquipamientoResponse.de(
                gestion.equipar(identidad.propietario(autenticacion, cabecera), heroeId, elementoId));
    }

    @DeleteMapping("/{elementoId}")
    public EquipamientoResponse desequipar(
            Authentication autenticacion,
            @RequestHeader(name = CABECERA_IDENTIDAD, required = false) String cabecera,
            @PathVariable String heroeId,
            @PathVariable String elementoId) {
        return EquipamientoResponse.de(
                gestion.desequipar(identidad.propietario(autenticacion, cabecera), heroeId, elementoId));
    }
}
