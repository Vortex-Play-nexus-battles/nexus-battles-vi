package nexus.inventario.api;

import java.util.UUID;

import nexus.inventario.aplicacion.CategoriaInventario;
import nexus.inventario.aplicacion.ConsultarInventarioPaginado;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * El inventario de una cuenta para su ficha administrativa — inventario.yaml
 * 1.8.0 (§7.3.4, «ver y modificar inventario del usuario»).
 *
 * <p>Es la unica lectura del inventario donde el dueño sale de la ruta y no
 * del token: solo la abren Administrador y Super Administrador
 * ({@code SeguridadConfig}). Misma pagina de dieciseis y mismas categorias que
 * la vitrina del jugador. Modificar es {@code POST /entregas} con origen
 * {@code ADMINISTRACION}, que ya existia.
 */
@RestController
@RequestMapping("/api/v1/inventario/usuarios")
public class InventarioDeUsuarioController {

    private final ConsultarInventarioPaginado consulta;

    public InventarioDeUsuarioController(ConsultarInventarioPaginado consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{uid}/elementos")
    public PaginaInventarioResponse consultarPagina(
            @PathVariable String uid,
            @RequestParam(name = "pagina", defaultValue = "0") int pagina,
            @RequestParam(name = "categoria", required = false) CategoriaInventario categoria) {
        return PaginaInventarioResponse.de(consulta.consultar(uidValido(uid), pagina, categoria));
    }

    /** El inventario se guarda por {@code uid}: cualquier otra cosa no nombra a nadie. */
    static String uidValido(String uid) {
        try {
            return UUID.fromString(uid.strip()).toString();
        } catch (IllegalArgumentException noEsUid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El uid de la cuenta no es valido.");
        }
    }
}
