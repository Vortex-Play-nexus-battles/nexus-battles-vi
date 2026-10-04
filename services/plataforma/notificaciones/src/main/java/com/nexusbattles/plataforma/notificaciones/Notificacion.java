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

    /**
     * Largo maximo del identificador (notificaciones.yaml 1.2.0).
     *
     * <p>Auditoria de DEV del 30-sep: la columna era de 64 y torneos manda
     * identificadores de 101 a 106 caracteres ({@code torneo-<uuid>-jugador-
     * <uuid>-aviso-<hito>}). La base rechazaba el insert, el manejador lo
     * confundia con un duplicado y respondia 409, y torneos —que trata el 409
     * como «ya estaba»— daba el aviso por entregado: se perdia en silencio.
     */
    public static final int LARGO_MAXIMO_ID = 200;

    /** Largo maximo del tipo, el de su columna. */
    public static final int LARGO_MAXIMO_TIPO = 40;

    /** Largo maximo del titulo, el de su columna. */
    public static final int LARGO_MAXIMO_TITULO = 200;

    public Notificacion {
        exigirTexto(id, "el identificador de la notificacion");
        exigirTexto(tipo, "el tipo de la notificacion");
        exigirTexto(titulo, "el titulo de la notificacion");
        exigirTexto(cuerpo, "el cuerpo de la notificacion");
        Objects.requireNonNull(creadaEn, "la fecha de creacion de la notificacion es obligatoria");
        exigirLargo(id, LARGO_MAXIMO_ID, "el identificador de la notificacion");
        exigirLargo(tipo, LARGO_MAXIMO_TIPO, "el tipo de la notificacion");
        exigirLargo(titulo, LARGO_MAXIMO_TITULO, "el titulo de la notificacion");
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }

    /** Un dato que no cabe se rechaza con 400, nunca se recorta ni se confunde con un duplicado. */
    private static void exigirLargo(String valor, int maximo, String campo) {
        if (valor.length() > maximo) {
            throw new IllegalArgumentException(
                    campo + " admite como mucho " + maximo + " caracteres y llegaron " + valor.length());
        }
    }
}
