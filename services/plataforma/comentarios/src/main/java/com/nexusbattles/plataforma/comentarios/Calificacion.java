package com.nexusbattles.plataforma.comentarios;

import java.time.Instant;
import java.util.Objects;

/**
 * La opinion en estrellas de un jugador sobre un producto — 7.1 del documento
 * del curso, RF-COM-002; separada del comentario en B3 (contrato 1.5.0).
 *
 * <p>El 7.1 dice dos cosas que un solo registro no podia cumplir a la vez:
 * «Los usuarios solo pueden calificar un producto una vez, pero podran agregar
 * o retirar tantos comentarios como sea de su agrado». Mientras las estrellas
 * vivian dentro del comentario, calificar exigia escribir, retirar un
 * comentario arrastraba la calificacion, y un comentario ocultado por
 * moderacion dejaba al autor sin poder calificar y sin que su calificacion
 * contara. Separadas, cada regla tiene su sitio: los comentarios van y vienen,
 * la calificacion se da una vez y se queda.
 *
 * <p>Sin edicion ni retirada a proposito: el 7.1 no las contempla y el
 * contrato 1.4.0 las excluye («una sola calificacion por jugador y producto,
 * sin edicion ni retirada»). Que sea una sola la garantiza la base de datos
 * con una restriccion unica, no esta clase: dos peticiones simultaneas del
 * mismo jugador no se ven entre si, la restriccion si.
 *
 * @param id identificador de la calificacion
 * @param productoId producto calificado
 * @param autorId jugador que califica (el {@code uid} del token)
 * @param estrellas de 1 a 5
 * @param creadaEn cuando se registro
 */
public record Calificacion(String id, String productoId, String autorId, int estrellas, Instant creadaEn) {

    /** La escala del 7.1: «valorada en cinco estrellas». */
    public static final int MINIMO_DE_ESTRELLAS = 1;
    public static final int MAXIMO_DE_ESTRELLAS = 5;

    public Calificacion {
        exigirTexto(id, "el identificador de la calificacion");
        exigirTexto(productoId, "el identificador del producto");
        exigirTexto(autorId, "el identificador del autor");
        exigirEstrellas(estrellas);
        Objects.requireNonNull(creadaEn, "la fecha de la calificacion es obligatoria");
    }

    /**
     * Comprueba unas estrellas que llegan de fuera, que pueden no venir.
     *
     * @return las mismas estrellas, ya sabiendo que estan en la escala
     * @throws IllegalArgumentException si faltan o estan fuera de 1..5 (400)
     */
    public static int exigirEstrellas(Integer estrellas) {
        if (estrellas == null || estrellas < MINIMO_DE_ESTRELLAS || estrellas > MAXIMO_DE_ESTRELLAS) {
            throw new IllegalArgumentException(
                    "la calificacion va de " + MINIMO_DE_ESTRELLAS + " a " + MAXIMO_DE_ESTRELLAS
                            + " estrellas, llego " + estrellas);
        }
        return estrellas;
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }
}
