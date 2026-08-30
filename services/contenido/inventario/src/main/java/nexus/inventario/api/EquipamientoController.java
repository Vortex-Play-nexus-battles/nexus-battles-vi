package nexus.inventario.api;

import nexus.inventario.aplicacion.GestionarEquipamiento;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventario/heroes/{heroeId}/equipamiento")
public class EquipamientoController {

    private static final String CABECERA_IDENTIDAD = "X-User-Name";
    private final GestionarEquipamiento gestion;

    public EquipamientoController(GestionarEquipamiento gestion) {
        this.gestion = gestion;
    }

    @GetMapping
    public EquipamientoResponse consultar(
            @RequestHeader(name = CABECERA_IDENTIDAD, required = false) String identidad,
            @PathVariable String heroeId) {
        return EquipamientoResponse.de(gestion.consultar(identidad, heroeId));
    }

    @PutMapping("/{elementoId}")
    public EquipamientoResponse equipar(
            @RequestHeader(name = CABECERA_IDENTIDAD, required = false) String identidad,
            @PathVariable String heroeId,
            @PathVariable String elementoId) {
        return EquipamientoResponse.de(gestion.equipar(identidad, heroeId, elementoId));
    }

    @DeleteMapping("/{elementoId}")
    public EquipamientoResponse desequipar(
            @RequestHeader(name = CABECERA_IDENTIDAD, required = false) String identidad,
            @PathVariable String heroeId,
            @PathVariable String elementoId) {
        return EquipamientoResponse.de(gestion.desequipar(identidad, heroeId, elementoId));
    }
}
