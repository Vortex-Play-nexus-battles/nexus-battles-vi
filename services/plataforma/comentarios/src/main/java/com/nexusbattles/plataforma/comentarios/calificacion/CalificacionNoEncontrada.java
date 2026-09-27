package com.nexusbattles.plataforma.comentarios.calificacion;

/**
 * El jugador todavia no califico ese producto — 404 de {@code GET
 * /products/{productId}/rating/mia} (contrato 1.4.0). No es un fallo: es el
 * estado normal de quien aun no ha opinado, y la ficha lo usa para ofrecerle
 * las estrellas.
 */
public class CalificacionNoEncontrada extends RuntimeException {

    public CalificacionNoEncontrada(String productoId) {
        super("todavia no calificaste el producto " + productoId);
    }
}
