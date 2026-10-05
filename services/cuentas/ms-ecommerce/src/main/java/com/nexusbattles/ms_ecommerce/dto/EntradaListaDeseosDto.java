package com.nexusbattles.ms_ecommerce.dto;

import java.time.Instant;

/**
 * Un producto de la lista de deseos ({@code EntradaListaDeseos} del contrato
 * 1.3.0): {@code {"productoId":"<uuid>","nombre":"...","imagen":"...","agregadoEn":"..."}}.
 *
 * @param nombre el del catalogo; el que tenia al guardarlo si el catalogo no responde
 * @param imagen la del catalogo; null si no se sabe
 */
public record EntradaListaDeseosDto(String productoId, String nombre, String imagen, Instant agregadoEn) {
}
