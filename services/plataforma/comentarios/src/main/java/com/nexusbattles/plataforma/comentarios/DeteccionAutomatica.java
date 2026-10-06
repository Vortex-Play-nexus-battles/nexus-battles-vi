package com.nexusbattles.plataforma.comentarios;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Por que el filtro automatico retuvo un comentario. HU-COM-007 CA-01,
 * contrato comentarios 1.10.0.
 *
 * <p>Hasta la 1.10.0 el servicio se quedaba solo con el «si» o el «no» de la
 * lista negra: el comentario entraba en EN_REVISION y el moderador no sabia que
 * termino lo habia senalado. Ahora se guarda lo que la lista negra explico al
 * verificarlo, y la cola y el detalle lo devuelven.
 *
 * <p>Nunca lleva el texto del comentario ni los terminos coincidentes: las
 * reglas se nombran por su {@code id} (los {@code reglas} de
 * moderacion-lista-negra 2.1.0), que la consola de lista negra resuelve.
 *
 * <p>Lo que llega de fuera se acomoda a lo que la bandeja guarda, y no al
 * reves: la categoria y el motivo se recortan a sus columnas (V7) y una regla
 * nula se descarta. Una respuesta rara de la lista negra no puede impedir que
 * el comentario quede retenido, que es lo que pide RF-COM-007.
 *
 * @param fecha                cuando se verifico el texto, al publicar
 * @param reglas               los {@code id} de los terminos que coincidieron;
 *                             vacia si la lista negra no respondio
 * @param categoria            la categoria que informo la lista negra; nula si
 *                             no respondio o no la dio
 * @param motivo               el mensaje generico de la lista negra (sin el
 *                             termino) o, si no respondio, la razon de la retencion
 * @param servicioNoDisponible si se retuvo porque la lista negra no respondio
 */
public record DeteccionAutomatica(
        Instant fecha,
        List<Long> reglas,
        String categoria,
        String motivo,
        boolean servicioNoDisponible) {

    /** Lo que cabe en {@code comentario_detecciones.categoria} (V7). */
    public static final int CATEGORIA_MAXIMA = 32;

    /** Lo que cabe en {@code comentario_detecciones.motivo} (V7). */
    public static final int MOTIVO_MAXIMO = 500;

    public DeteccionAutomatica {
        Objects.requireNonNull(fecha, "la fecha de la verificacion es obligatoria");
        reglas = reglas == null ? List.of() : reglas.stream().filter(Objects::nonNull).toList();
        categoria = recortar(categoria, CATEGORIA_MAXIMA);
        motivo = recortar(motivo, MOTIVO_MAXIMO);
    }

    private static String recortar(String valor, int maximo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.strip();
        return limpio.length() > maximo ? limpio.substring(0, maximo) : limpio;
    }
}
