package nexus.recursos;

import java.util.List;

/**
 * Una página de la biblioteca de recursos ({@code PaginaDeRecursos},
 * productos.yaml 1.10.0), con los mismos nombres que la del catálogo.
 */
public record PaginaDeRecursos(
        List<RecursoGraficoVista> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PaginaDeRecursos {
        content = List.copyOf(content);
    }
}
