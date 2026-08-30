package nexus.inventario.aplicacion;

import java.util.List;
import java.util.Objects;
import nexus.inventario.dominio.ElementoInventario;

/** Resultado estable de una consulta paginada del inventario. */
public record PaginaInventario(
        List<ElementoInventario> elementos,
        int numero,
        int tamanio,
        int totalElementos,
        int totalPaginas,
        boolean ultima) {

    public PaginaInventario {
        elementos = List.copyOf(Objects.requireNonNull(
                elementos, "elementos no puede ser nulo"));
        if (numero < 0 || tamanio <= 0 || totalElementos < 0 || totalPaginas < 0) {
            throw new IllegalArgumentException("Los metadatos de paginacion no pueden ser negativos");
        }
    }
}
