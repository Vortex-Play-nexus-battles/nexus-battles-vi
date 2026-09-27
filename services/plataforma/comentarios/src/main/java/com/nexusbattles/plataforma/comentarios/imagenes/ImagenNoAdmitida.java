package com.nexusbattles.plataforma.comentarios.imagenes;

/**
 * No es una imagen JPEG, PNG o WebP valida — 415 {@code imagen-no-admitida}
 * con {@code motivo: FORMATO_DE_IMAGEN_NO_ADMITIDO} (contrato 1.4.0).
 *
 * <p>Cubre los tres casos que importan: una firma de bytes que no es de
 * ninguno de los tres formatos (un HTML con extension .png), una estructura
 * rota (un PNG truncado) y un archivo con algo pegado detras del final de la
 * imagen (un poliglota: imagen valida por delante, otro documento por detras).
 */
public class ImagenNoAdmitida extends RuntimeException {

    public ImagenNoAdmitida(String explicacion) {
        super(explicacion);
    }
}
