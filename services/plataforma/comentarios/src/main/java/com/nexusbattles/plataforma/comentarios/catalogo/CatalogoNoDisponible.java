package com.nexusbattles.plataforma.comentarios.catalogo;

/**
 * El catalogo no respondio y no se puede saber si el producto existe — 503
 * {@code catalogo-no-disponible}.
 *
 * <p>Misma postura que {@code SancionesNoDisponibles}: una escritura sobre un
 * producto que quiza no existe no se acepta «por si acaso». El jugador
 * reintenta en un momento; lo que no se hace es guardar un comentario o una
 * calificacion que luego no cuelgan de nada.
 */
public class CatalogoNoDisponible extends RuntimeException {

    public CatalogoNoDisponible(String motivo) {
        super("el catalogo de productos no respondio y no se pudo comprobar el producto;"
                + " intenta de nuevo en un momento (" + motivo + ")");
    }
}
