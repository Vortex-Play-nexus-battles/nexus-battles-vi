package com.nexusbattles.ms_subastas.subastas.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * HU-SUB-011. Envoltorio de paginacion con los nombres de campo exactos del
 * contrato (contenido/pagina/tamanoPagina/totalElementos/totalPaginas) --
 * a proposito, en vez de serializar un Page de Spring Data directo, cuyo
 * JSON (content/number/size/totalElements/...) no coincidiria con el
 * contrato ya publicado.
 */
public record PaginaDeSubastasResponse(
    List<SubastaResumenResponse> contenido,
    int pagina,
    int tamanoPagina,
    long totalElementos,
    int totalPaginas
) {

    public static PaginaDeSubastasResponse desde(Page<SubastaResumenResponse> page) {
        return new PaginaDeSubastasResponse(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
