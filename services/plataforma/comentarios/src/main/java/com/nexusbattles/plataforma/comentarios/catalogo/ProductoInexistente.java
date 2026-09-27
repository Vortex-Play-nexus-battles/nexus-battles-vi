package com.nexusbattles.plataforma.comentarios.catalogo;

/**
 * El catalogo dice que ese producto no existe — 404 {@code producto-inexistente}
 * (contrato 1.4.0, respuesta {@code ProductoInexistente}).
 */
public class ProductoInexistente extends RuntimeException {

    public ProductoInexistente(String productoId) {
        super("el producto " + productoId + " no existe en el catalogo");
    }
}
