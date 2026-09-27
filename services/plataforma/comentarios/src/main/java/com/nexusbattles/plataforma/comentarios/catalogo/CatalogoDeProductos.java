package com.nexusbattles.plataforma.comentarios.catalogo;

/**
 * Puerta al catalogo de productos, que es de otro servicio (contenido/productos)
 * — B3, contrato comentarios 1.5.0.
 *
 * <p>Hasta B3 el {@code productId} de la ruta se aceptaba tal cual: se podia
 * comentar y calificar un producto que no existia, y la auditoria lo encontro
 * con hilos colgando de identificadores inventados. Ahora las escrituras
 * preguntan antes al catalogo, por su API publica
 * ({@code GET /api/v1/productos/{id}}, productos.yaml) y nunca por su base
 * (regla 7 de plataforma).
 *
 * <p>Las dos preguntas son distintas a proposito. Una escritura no se acepta a
 * ciegas: si el catalogo no contesta, {@link #exigirExistente} falla con 503
 * en vez de guardar algo sobre un producto que quiza no existe. Una lectura si
 * puede degradarse: {@link #existencia} dice {@link Existencia#DESCONOCIDA} y
 * quien lee decide servir lo que tiene (HU-DIS-003, degradacion controlada).
 */
public interface CatalogoDeProductos {

    /** Lo que se sabe de un producto. */
    enum Existencia {
        /** El catalogo lo tiene. */
        EXISTE,
        /** El catalogo respondio que no existe (404). */
        NO_EXISTE,
        /** El catalogo no respondio, o respondio algo que no se puede creer. */
        DESCONOCIDA
    }

    /**
     * @throws ProductoInexistente  si el catalogo dice que no existe (404)
     * @throws CatalogoNoDisponible si el catalogo no responde (503): no se
     *                              acepta nada a ciegas
     */
    void exigirExistente(String productoId);

    /** Para lecturas que pueden degradarse: nunca lanza por una caida del catalogo. */
    Existencia existencia(String productoId);
}
