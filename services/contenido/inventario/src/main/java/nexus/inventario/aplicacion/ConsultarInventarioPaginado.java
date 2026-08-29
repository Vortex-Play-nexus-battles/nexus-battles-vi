package nexus.inventario.aplicacion;

import java.util.List;
import java.util.Objects;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de HU-INV-001: consulta el inventario propio en paginas de 16.
 *
 * <p>El tamano fijo sale del criterio 1 de la historia, que exige dieciseis
 * productos en la vitrina a la resolucion de referencia.</p>
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
        String propietarioId = exigirIdentidad(identidad);
        if (numeroPagina < 0) {
            throw new IllegalArgumentException("numeroPagina no puede ser negativo");
        }

        List<ElementoInventario> todos = repositorio.buscarPorPropietario(propietarioId)
                .map(Inventario::elementos)
                .orElseGet(List::of);
        int totalElementos = todos.size();
        int totalPaginas = totalElementos == 0
                ? 0
                : (totalElementos + TAMANIO_PAGINA - 1) / TAMANIO_PAGINA;

        long inicioCalculado = (long) numeroPagina * TAMANIO_PAGINA;
        int inicio = (int) Math.min(inicioCalculado, totalElementos);
        int fin = Math.min(inicio + TAMANIO_PAGINA, totalElementos);
        List<ElementoInventario> elementos = todos.subList(inicio, fin);
        boolean ultima = totalPaginas == 0 || numeroPagina >= totalPaginas - 1;

        return new PaginaInventario(
                elementos, numeroPagina, TAMANIO_PAGINA, totalElementos, totalPaginas, ultima);
    }

    /** Misma regla de identidad que {@link GestionarInventario}. */
    private String exigirIdentidad(String identidad) {
        if (identidad == null || identidad.isBlank()) {
            throw new IdentidadRequeridaException();
        }
        return identidad.trim();
    }
}
