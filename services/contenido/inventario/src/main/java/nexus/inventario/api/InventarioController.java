package nexus.inventario.api;

import nexus.inventario.aplicacion.ConsultarInventarioPaginado;
import nexus.inventario.aplicacion.PaginaInventario;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** API consumida por la vitrina del inventario. */
@RestController
@RequestMapping("/api/v1/inventarios")
public class InventarioController {

    private final ConsultarInventarioPaginado consulta;

    public InventarioController(ConsultarInventarioPaginado consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{propietarioId}/elementos")
    public PaginaInventario consultar(
            @PathVariable String propietarioId,
            @RequestParam(defaultValue = "0") int pagina) {
        if (pagina < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "pagina no puede ser negativa");
        }
        return consulta.consultar(propietarioId, pagina);
    }
}
