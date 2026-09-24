package com.nexusbattles.ms_identidad.admin.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envoltorio de pagina para las respuestas administrativas.
 *
 * Existe para no publicar el {@code Page} de Spring tal cual: ese objeto
 * arrastra en el JSON la forma interna del paginador (`pageable`, `sort`,
 * `first`, `empty`...) y convierte un detalle de la biblioteca en parte del
 * contrato. Aqui van los cinco campos que la tabla necesita y ninguno mas.
 */
public record PaginaAdminResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long total,
        int totalPaginas) {

    public static <E, T> PaginaAdminResponse<T> desde(Page<E> page, Function<E, T> convertir) {
        return new PaginaAdminResponse<>(
                page.getContent().stream().map(convertir).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
