package com.nexusbattles.plataforma.comentarios.imagenes;

/**
 * La imagen pesa mas de 2 MB o su cabecera describe mas de 4096x4096 pixeles
 * — 413 {@code imagen-demasiado-grande} (contrato 1.5.0).
 *
 * <p>Las dos cosas van al mismo codigo porque para quien sube la foto son la
 * misma: «es demasiado grande». La segunda protege ademas al que la ve: una
 * imagen de pocos kilobytes puede declarar dimensiones que, al pintarse,
 * piden cientos de megas de memoria (una «bomba de descompresion»).
 */
public class ImagenDemasiadoGrande extends RuntimeException {

    public ImagenDemasiadoGrande(String explicacion) {
        super(explicacion);
    }
}
