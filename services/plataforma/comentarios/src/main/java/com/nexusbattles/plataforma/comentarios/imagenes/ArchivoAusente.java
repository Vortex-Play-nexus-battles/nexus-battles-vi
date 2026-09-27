package com.nexusbattles.plataforma.comentarios.imagenes;

/** No llego el campo {@code archivo}, o llego vacio — 400 {@code imagen-ausente}. */
public class ArchivoAusente extends RuntimeException {

    public ArchivoAusente() {
        super("falta la imagen: se sube en el campo «archivo» de un formulario multipart");
    }
}
