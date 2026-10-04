package com.nexusbattles.ms_ecommerce.compra;

/**
 * Lo que devuelve {@code POST /checkout} cuando la respuesta es una orden.
 *
 * @param creada true si esta peticion creo o cobro la orden (201); false si la
 *               clave ya se habia usado y es la orden original, sin repetir
 *               nada (200)
 */
public record ResultadoDeCompra(OrdenDto orden, boolean creada) {
}
