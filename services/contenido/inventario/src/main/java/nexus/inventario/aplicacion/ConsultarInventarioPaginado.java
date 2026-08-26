package nexus.inventario.aplicacion;

import java.util.List;
import java.util.Objects;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import org.springframework.stereotype.Service;

/** Caso de uso de SCRUM-318: consulta el inventario en paginas de 16. */
@Service
public class ConsultarInventarioPaginado {

    public static final int TAMANIO_PAGINA = 16;

    private final RepositorioDeInventarios repositorio;

    public ConsultarInventarioPaginado(RepositorioDeInventarios repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio es obligatorio");
    }

    public PaginaInventario consultar(String propietarioId, int numeroPagina) {
        exigirPropietario(propietarioId);
        if (numeroPagina < 0) {
            throw new IllegalArgumentException("numeroPagina no puede ser negativo");
        }

        Inventario inventario = repositorio.buscarPorPropietario(propietarioId)
                .orElseGet(() -> Inventario.vacio(propietarioId));
        List<ElementoInventario> todos = inventario.elementos();
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
                elementos,
                numeroPagina,
                TAMANIO_PAGINA,
                totalElementos,
                totalPaginas,
                ultima);
    }

    private void exigirPropietario(String propietarioId) {
        if (propietarioId == null || propietarioId.isBlank()) {
            throw new IllegalArgumentException("propietarioId no puede estar vacio");
        }
    }
}
