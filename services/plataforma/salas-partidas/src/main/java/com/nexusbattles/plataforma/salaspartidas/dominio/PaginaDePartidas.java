package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.List;

/**
 * Una pagina del historial de partidas de un jugador (1.7.0), de la mas
 * reciente a la mas antigua.
 */
public record PaginaDePartidas(List<Partida> contenido, int pagina, int tamano, long totalElementos,
                               int totalPaginas) {

    public PaginaDePartidas {
        contenido = contenido == null ? List.of() : List.copyOf(contenido);
    }
}
