package nexus.inventario.api;

import nexus.inventario.aplicacion.ConsultarHeroeActivo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventario/heroes/activo")
public class HeroeActivoController {

    private static final String CABECERA_IDENTIDAD = "X-User-Name";
    private final ConsultarHeroeActivo consulta;

    public HeroeActivoController(ConsultarHeroeActivo consulta) {
        this.consulta = consulta;
    }

    @GetMapping
    public ResponseEntity<HeroeActivoResponse> consultar(
            @RequestHeader(name = CABECERA_IDENTIDAD, required = false) String identidad) {
        return consulta.consultar(identidad)
                .map(heroeActivo -> ResponseEntity.ok(HeroeActivoResponse.de(heroeActivo)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
