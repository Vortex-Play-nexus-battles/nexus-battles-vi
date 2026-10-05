package com.nexusbattles.plataforma.comentarios.publicacion;

import java.time.Instant;

import com.nexusbattles.plataforma.comentarios.Comentario;

/**
 * Lo minimo de un comentario para decidir sobre su autor — HU-COM-005, contrato
 * 1.7.0 ({@code ItemDelHistorial}).
 *
 * <p>Es una proyeccion de {@link ComentarioRepository#findByAutorId}: la consulta
 * trae solo estas columnas. No pasa por {@link RegistroDeComentario} a proposito,
 * porque la entidad arrastra la coleccion de imagenes (carga diferida) que el
 * historial no devuelve y que costaria una consulta mas por pagina. Tampoco lleva
 * las estrellas, la marca de seguimiento ni el autor: el autor es el de la ruta.
 */
public record ResumenDeComentario(
        String id,
        String productoId,
        String apodoAutor,
        String texto,
        Instant fechaPublicacion,
        Comentario.Estado estado,
        boolean editado) {
}
