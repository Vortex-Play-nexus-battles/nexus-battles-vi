package nexus.inventario.aplicacion;

import java.util.List;
import java.util.Objects;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import org.springframework.stereotype.Service;

/** Busca elementos propios mediante el indice de texto del inventario. */
@Service
public class BuscarElementosInventario {

    private static final int MINIMO_CARACTERES = 4;
    private final RepositorioDeInventarios repositorio;

    public BuscarElementosInventario(RepositorioDeInventarios repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio es obligatorio");
    }

    public PaginaInventario buscar(String identidad, String criterio, int numeroPagina) {
        String propietarioId = exigirIdentidad(identidad);
        String texto = exigirCriterio(criterio);
        if (numeroPagina < 0) {
            throw new IllegalArgumentException("numeroPagina no puede ser negativo");
        }

        List<ElementoInventario> coincidencias = repositorio.buscarElementos(propietarioId, texto);
        return paginar(coincidencias, numeroPagina);
    }

    private PaginaInventario paginar(List<ElementoInventario> elementos, int numeroPagina) {
        int tamanio = ConsultarInventarioPaginado.TAMANIO_PAGINA;
        int totalElementos = elementos.size();
        int totalPaginas = totalElementos == 0 ? 0 : (totalElementos + tamanio - 1) / tamanio;
        long inicioCalculado = (long) numeroPagina * tamanio;
        int inicio = (int) Math.min(inicioCalculado, totalElementos);
        int fin = Math.min(inicio + tamanio, totalElementos);
        boolean ultima = totalPaginas == 0 || numeroPagina >= totalPaginas - 1;
        return new PaginaInventario(
                elementos.subList(inicio, fin), numeroPagina, tamanio,
                totalElementos, totalPaginas, ultima);
    }

    private String exigirIdentidad(String identidad) {
        if (identidad == null || identidad.isBlank()) {
            throw new IdentidadRequeridaException();
        }
        return identidad.trim();
    }

    private String exigirCriterio(String criterio) {
        if (criterio == null || criterio.trim().length() < MINIMO_CARACTERES) {
            throw new CriterioBusquedaInvalidoException();
        }
        return criterio.trim();
    }
}
