package nexus.api;

import java.util.List;

/**
 * Una pagina del listado del catalogo — {@code GET /api/v1/productos}, R16,
 * contrato productos 1.2.0 (esquema {@code PaginaDeProductos}).
 *
 * <p>Los nombres de los componentes son los del JSON que fija el contrato
 * ({@code content, page, size, totalElements, totalPages}); cada elemento de
 * {@code content} tiene la misma forma que la respuesta de
 * {@code GET /api/v1/productos/{id}}.
 *
 * <p>Se publica este registro y no el {@code Page} de Spring Data tal cual:
 * la forma serializada de {@code Page} no es estable entre versiones y el
 * contrato no puede depender de ella.
 */
public record PaginaDeProductos(
        List<ProductoCreado> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

        public PaginaDeProductos {
                content = List.copyOf(content);
        }
}
