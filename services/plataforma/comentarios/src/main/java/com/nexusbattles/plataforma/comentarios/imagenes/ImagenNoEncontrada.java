package com.nexusbattles.plataforma.comentarios.imagenes;

/**
 * No hay imagen con ese id, o quien la pide no puede verla — 404
 * {@code imagen-no-encontrada}.
 *
 * <p>Las dos cosas responden igual a proposito: un 403 para las imagenes que
 * existen pero no son visibles diria que existen, y de un id de imagen de un
 * comentario en revision no tiene por que enterarse nadie mas que su autor y
 * moderacion.
 */
public class ImagenNoEncontrada extends RuntimeException {

    public ImagenNoEncontrada(String imagenId) {
        super("no hay ninguna imagen " + imagenId + " que puedas ver");
    }
}
