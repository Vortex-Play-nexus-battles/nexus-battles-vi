package nexus.inventario.aplicacion;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de HU-INV-001: consulta el inventario propio en paginas de 16.
 *
 * <p>El tamano fijo sale del criterio 1 de la historia, que exige dieciseis
 * productos en la vitrina a la resolucion de referencia.</p>
 *
 * <p>1.7.0: la consulta puede pedirse por {@link CategoriaInventario} (héroes u
 * objetos), y cada página dice qué héroe lleva cada elemento ({@link EquipadoEn}).
 * Así cada pestaña de la vista pide al servidor solo la página que enseña.</p>
 */
@Service
public class ConsultarInventarioPaginado {

    /** Productos por pagina, segun el criterio 1 de HU-INV-001. */
    public static final int TAMANIO_PAGINA = 16;

    private final RepositorioDeInventarios repositorio;

    public ConsultarInventarioPaginado(RepositorioDeInventarios repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio es obligatorio");
    }

    /**
     * Devuelve una pagina del inventario de quien consulta.
     *
     * <p>La identidad llega en la cabecera y nunca en la ruta: asi un jugador
     * no puede pedir el inventario de otro.</p>
     */
    public PaginaInventario consultar(String identidad, int numeroPagina) {
        return consultar(identidad, numeroPagina, null);
    }

    /**
     * @param categoria héroes u objetos; {@code null} pagina el inventario entero,
     *                  como antes de 1.7.0
     */
    public PaginaInventario consultar(String identidad, int numeroPagina, CategoriaInventario categoria) {
        String propietarioId = exigirIdentidad(identidad);
        if (numeroPagina < 0) {
            throw new IllegalArgumentException("numeroPagina no puede ser negativo");
        }

        Optional<Inventario> inventario = repositorio.buscarPorPropietario(propietarioId);
        List<ElementoInventario> todos = inventario.map(Inventario::elementos).orElseGet(List::of);
        List<ElementoInventario> deLaCategoria = categoria == null
                ? todos
                : todos.stream().filter(categoria::incluye).toList();
        Map<String, EquipadoEn> equipados =
                inventario.map(PaginaInventario::equipadosDe).orElseGet(Map::of);
        return PaginaInventario.de(deLaCategoria, numeroPagina, equipados);
    }

    /** Misma regla de identidad que {@link GestionarInventario}. */
    private String exigirIdentidad(String identidad) {
        if (identidad == null || identidad.isBlank()) {
            throw new IdentidadRequeridaException();
        }
        return identidad.trim();
    }
}
