package com.nexusbattles.plataforma.comentarios.moderacion;

import com.nexusbattles.plataforma.comentarios.Comentario;

/**
 * Avisar al autor de que su comentario se resolvio — RF-COM-008, CA-04.
 *
 * <h2>Por que devuelve boolean en vez de lanzar</h2>
 *
 * Porque el aviso es fail-open a proposito y quien llama tiene que poder
 * decirlo. Un servicio de notificaciones caido no puede impedir que se retire
 * un comentario ofensivo: la decision del moderador es lo importante y el
 * aviso es su consecuencia, no su condicion. Pero tampoco se traga el fallo en
 * silencio — la respuesta lleva {@code autorNotificado}, y el moderador ve que
 * el autor no se entero.
 *
 * <p>Es la misma postura que HU-DIS-003 pide para las dependencias no
 * criticas: degradacion controlada, nunca fallo silencioso.
 */
public interface AvisoAlAutor {

    /**
     * @return si el aviso salio. {@code false} no invalida la decision.
     */
    boolean notificar(Comentario comentario, AsientoDeModeracion asiento);
}
