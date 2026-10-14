package com.nexusbattles.ms_chatbot.chat.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// productos.yaml: PaginaDeProductos (GET /api/v1/productos).
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaginaDelCatalogoDto(List<ProductoDelCatalogoDto> content, int page, int totalPages,
                                   long totalElements) {
}
