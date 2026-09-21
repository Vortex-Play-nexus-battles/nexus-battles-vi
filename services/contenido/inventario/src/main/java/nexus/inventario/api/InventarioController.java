package nexus.inventario.api;

import jakarta.validation.Valid;
import java.net.URI;
import nexus.inventario.aplicacion.BuscarElementosInventario;
import nexus.inventario.aplicacion.ConsultarElementoInventario;
import nexus.inventario.aplicacion.ConsultarInventarioPaginado;
import nexus.inventario.aplicacion.GestionarInventario;
import nexus.inventario.configuracion.IdentidadDelLlamador;
import nexus.inventario.dominio.ElementoInventario;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inventario del jugador (HU-INV-001 en adelante).
 *
 * <p>El propietario sale de {@link IdentidadDelLlamador}: el apodo del token
 * cuando llama un jugador, o {@code X-User-Name} cuando llama un servicio con
 * credencial (ADR-005). La cabecera sola ya no identifica a nadie.
 */
@RestController
@RequestMapping("/api/v1/inventario/elementos")
public class InventarioController {

    private static final String CABECERA_IDENTIDAD = IdentidadDelLlamador.CABECERA_PROPIETARIO;
    private final GestionarInventario gestion;
    private final ConsultarInventarioPaginado consulta;
    private final BuscarElementosInventario busqueda;
    private final ConsultarElementoInventario consultaElemento;
    private final IdentidadDelLlamador identidad;

    public InventarioController(
            GestionarInventario gestion,
            ConsultarInventarioPaginado consulta,
            BuscarElementosInventario busqueda,
            ConsultarElementoInventario consultaElemento,
            IdentidadDelLlamador identidad) {
        this.gestion = gestion;
        this.consulta = consulta;
        this.busqueda = busqueda;
        this.consultaElemento = consultaElemento;
        this.identidad = identidad;
    }

    /**
     * HU-INV-001: la vitrina del jugador, en paginas de dieciseis.
     *
     * <p>Sin inventario todavia devuelve una pagina vacia con estado 200: que
     * el jugador no tenga nada no es un error.</p>
     */
    @GetMapping
    public PaginaInventarioResponse consultarPagina(
            Authentication autenticacion,
            @RequestHeader(name = CABECERA_IDENTIDAD, required = false) String cabecera,
            @RequestParam(name = "pagina", defaultValue = "0") int pagina) {
        return PaginaInventarioResponse.de(
                consulta.consultar(identidad.propietario(autenticacion, cabecera), pagina));
    }

    @GetMapping("/busqueda")
    public PaginaInventarioResponse buscar(
            Authentication autenticacion,
            @RequestHeader(name = CABECERA_IDENTIDAD, required = false) String cabecera,
            @RequestParam String criterio,
            @RequestParam(name = "pagina", defaultValue = "0") int pagina) {
        return PaginaInventarioResponse.de(
                busqueda.buscar(identidad.propietario(autenticacion, cabecera), criterio, pagina));
    }

    @GetMapping("/{elementoId}")
    public DetalleElementoInventarioResponse consultarElemento(@PathVariable String elementoId) {
        return DetalleElementoInventarioResponse.de(consultaElemento.consultar(elementoId));
    }

    @PostMapping
    public ResponseEntity<ElementoInventarioResponse> crear(
            Authentication autenticacion,
            @RequestHeader(name = CABECERA_IDENTIDAD, required = false) String cabecera,
            @Valid @RequestBody CrearElementoRequest solicitud) {
        ElementoInventario creado = gestion.crear(
                identidad.propietario(autenticacion, cabecera), solicitud.productoId(), solicitud.tipo(),
                solicitud.nombrePropio(), solicitud.parteArmadura());
        return ResponseEntity
                .created(URI.create("/api/v1/inventario/elementos/" + creado.id()))
                .body(ElementoInventarioResponse.de(creado));
    }

    @PatchMapping("/{elementoId}")
    public ElementoInventarioResponse modificar(
            Authentication autenticacion,
            @RequestHeader(name = CABECERA_IDENTIDAD, required = false) String cabecera,
            @PathVariable String elementoId,
            @Valid @RequestBody ModificarElementoRequest solicitud) {
        return ElementoInventarioResponse.de(gestion.modificarNombre(
                identidad.propietario(autenticacion, cabecera), elementoId, solicitud.nombrePropio()));
    }

    @DeleteMapping("/{elementoId}")
    public ResponseEntity<Void> eliminar(
            Authentication autenticacion,
            @RequestHeader(name = CABECERA_IDENTIDAD, required = false) String cabecera,
            @PathVariable String elementoId) {
        gestion.eliminar(identidad.propietario(autenticacion, cabecera), elementoId);
        return ResponseEntity.noContent().build();
    }
}
