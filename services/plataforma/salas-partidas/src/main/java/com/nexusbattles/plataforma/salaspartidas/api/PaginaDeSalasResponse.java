package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.plataforma.salaspartidas.dominio.PaginaDeSalas;

import java.util.List;

/**
 * Pagina de salas, calcada del esquema {@code PaginaDeSalas} del contrato.
 *
 * <p>Los cuatro numeros no son adorno: el componente {@code Paginacion} del
 * sistema de diseno escribe con ellos «Mostrando 16 de 38 salas», y su
 * descripcion explica por que — «el jugador necesita saber si merece la pena
 * seguir pasando paginas».
 */
public record PaginaDeSalasResponse(
        List<SalaResponse> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {

    static PaginaDeSalasResponse desde(PaginaDeSalas pagina) {
        return new PaginaDeSalasResponse(
                pagina.contenido().stream().map(SalaResponse::desde).toList(),
                pagina.pagina(),
                pagina.tamano(),
                pagina.totalElementos(),
                pagina.totalPaginas());
    }
}
