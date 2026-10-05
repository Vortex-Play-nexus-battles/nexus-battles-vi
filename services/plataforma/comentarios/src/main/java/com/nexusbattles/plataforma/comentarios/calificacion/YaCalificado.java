package com.nexusbattles.plataforma.comentarios.calificacion;

/**
 * El jugador ya califico ese producto — 409 {@code ya-calificado} (contrato
 * 1.4.0), con {@code motivo: CALIFICACION_DUPLICADA}.
 *
 * <p>Sale tanto si la calificacion ya estaba como si otra peticion simultanea
 * del mismo jugador gano la carrera: para quien la recibe es la misma
 * situacion, ya tiene su calificacion y no se cambia (7.1: «solo pueden
 * calificar un producto una vez»).
 */
public class YaCalificado extends RuntimeException {

    public YaCalificado(String productoId) {
        super("ya calificaste el producto " + productoId + "; la calificacion no se cambia ni se retira");
    }
}
