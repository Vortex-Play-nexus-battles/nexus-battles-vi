package com.nexusbattles.ms_ecommerce.dto;

import java.util.List;

/**
 * Una pagina de la vitrina: {@code {"content":[...],"number":0,"size":16,
 * "totalElements":N,"totalPages":M,"last":true}}.
 *
 * <p>Son los mismos nombres que tenia el {@code Page} de Spring Data que
 * devolvia la vitrina legada, que es lo que el frontend lee; pero es un
 * registro propio porque la forma serializada de {@code Page} no es estable
 * entre versiones y un contrato no puede depender de ella.
 *
 * @param number pagina actual, desde 0
 * @param last   true si no hay pagina despues de esta (tambien si la pedida
 *               ya cae fuera del total)
 */
public record PaginaDeVitrina(
        List<ProductoEnVentaDto> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {

    public PaginaDeVitrina {
        content = List.copyOf(content);
    }

    /**
     * Pagina localmente una lista ya filtrada (RN-PRD-010: 16 por pagina en
     * la vitrina). Una pagina fuera de rango no es un error: sale vacia, con
     * los totales de verdad, para que el cliente sepa donde termina.
     *
     * @throws IllegalArgumentException si la pagina es negativa o el tamano
     *         no es positivo; el controlador ya los filtra con un 400
     */
    public static PaginaDeVitrina de(List<ProductoEnVentaDto> todos, int numero, int tamano) {
        if (numero < 0 || tamano < 1) {
            throw new IllegalArgumentException("Pagina " + numero + " de tamano " + tamano + " no es una pagina valida");
        }
        int total = todos.size();
        int totalDePaginas = Math.ceilDiv(total, tamano);
        long desde = (long) numero * tamano;
        List<ProductoEnVentaDto> contenido = desde >= total
                ? List.of()
                : todos.subList((int) desde, (int) Math.min(desde + tamano, total));
        return new PaginaDeVitrina(contenido, numero, tamano, total, totalDePaginas, numero >= totalDePaginas - 1);
    }
}
