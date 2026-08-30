package com.nexusbattles.plataforma.comentarios;

import java.time.Instant;
import java.util.List;

/**
 * Lo que un jugador envia cuando quiere opinar sobre un producto. HU-COM-001.
 *
 * <p>Se separa del comentario ya guardado porque no son lo mismo. Aqui viene lo que
 * el jugador pidio, y puede que traiga estrellas aunque ya haya calificado antes.
 * Es el hilo el que decide que queda registrado al final.
 *
 * @param comentarioId identificador que se le asignara al comentario
 * @param autorId jugador que publica
 * @param apodoAutor apodo con el que aparecera
 * @param texto contenido escrito
 * @param imagenes adjuntos, puede venir vacia
 * @param estrellas calificacion pretendida, o nula si no quiere calificar
 * @param fecha momento del envio
 */
public record SolicitudDePublicacion(
        String comentarioId,
        String autorId,
        String apodoAutor,
        String texto,
        List<String> imagenes,
        Integer estrellas,
        Instant fecha) {

    public SolicitudDePublicacion {
        imagenes = imagenes == null ? List.of() : List.copyOf(imagenes);
    }
}
