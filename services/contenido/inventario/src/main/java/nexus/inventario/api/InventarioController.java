package nexus.inventario.api;

import jakarta.validation.Valid;
import java.net.URI;
import nexus.inventario.aplicacion.ConsultarInventarioPaginado;
import nexus.inventario.aplicacion.GestionarInventario;
import nexus.inventario.aplicacion.PaginaInventario;
import nexus.inventario.dominio.ElementoInventario;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventario/elementos")
public class InventarioController {

    private static final String CABECERA_IDENTIDAD = "X-User-Name";
    private final GestionarInventario gestion;
    private final ConsultarInventarioPaginado consulta;

    public InventarioController(
            GestionarInventario gestion, ConsultarInventarioPaginado consulta) {
        this.gestion = gestion;
        this.consulta = consulta;
    }

    /**
     * HU-INV-001: la vitrina del jugador, en paginas de dieciseis.
     *
     * <p>Sin inventario todavia devuelve una pagina vacia con estado 200: que
     * el jugador no tenga nada no es un error.</p>
     */
    @GetMapping
    public PaginaInventario consultarPagina(
            @RequestHeader(name = CABECERA_IDENTIDAD, required = false) String identidad,
            @RequestParam(name = "pagina", defaultValue = "0") int pagina) {
        return consulta.consultar(identidad, pagina);
    }

    @PostMapping
    public ResponseEntity<ElementoInventarioResponse> crear(
            @RequestHeader(name = CABECERA_IDENTIDAD, required = false) String identidad,
            @Valid @RequestBody CrearElementoRequest solicitud) {
        ElementoInventario creado = gestion.crear(
                identidad, solicitud.productoId(), solicitud.tipo(), solicitud.nombrePropio());
        return ResponseEntity
                .created(URI.create("/api/v1/inventario/elementos/" + creado.id()))
                .body(ElementoInventarioResponse.de(creado));
    }

    @PatchMapping("/{elementoId}")
    public ElementoInventarioResponse modificar(
            @RequestHeader(name = CABECERA_IDENTIDAD, required = false) String identidad,
            @PathVariable String elementoId,
            @Valid @RequestBody ModificarElementoRequest solicitud) {
        return ElementoInventarioResponse.de(
                gestion.modificarNombre(identidad, elementoId, solicitud.nombrePropio()));
    }
}
