package com.nexusbattles.ms_ecommerce.service;

/**
 * El catalogo no tiene el producto que se pide por su id en la ruta (lista de
 * deseos): 404 {@code producto-inexistente}. En el carrito, donde el id va en
 * el cuerpo, el mismo caso es un 422 (ver {@link ProductoNoAgregableException}).
 */
public class ProductoNoEncontradoException extends RuntimeException {

    public ProductoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
