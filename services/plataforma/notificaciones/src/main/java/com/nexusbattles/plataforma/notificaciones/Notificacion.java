package com.nexusbattles.plataforma.notificaciones;

import java.time.Instant;
import java.util.Objects;

/**
 * Aviso dirigido a un jugador. HU-NOT-006, requisito RF-NOT-006.
 *
 * <p>El modulo de notificaciones no genera eventos por su cuenta: escucha lo que
 * emiten los demas modulos y lo convierte en un aviso como este. Por eso la
 * notificacion es inmutable, no guarda estado de lectura y no sabe a que sesion
 * pertenece. El estado de lectura vive en la bandeja del usuario, no aqui, tal
 * como exige la regla de negocio de la historia.
 *
 * @param id identificador unico del aviso
 * @param tipo origen del evento, por ejemplo subasta, mision o sancion
 * @param titulo encabezado que ve el jugador
 * @param cuerpo texto del aviso
 * @param creadaEn momento en que se produjo el evento notificable
 */
public record Notificacion(String id, String tipo, String titulo, String cuerpo, Instant creadaEn) {

    public Notificacion {
        exigirTexto(id, "el identificador de la notificacion");
        exigirTexto(tipo, "el tipo de la notificacion");
        exigirTexto(titulo, "el titulo de la notificacion");
        exigirTexto(cuerpo, "el cuerpo de la notificacion");
        Objects.requireNonNull(creadaEn, "la fecha de creacion de la notificacion es obligatoria");
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }
}
