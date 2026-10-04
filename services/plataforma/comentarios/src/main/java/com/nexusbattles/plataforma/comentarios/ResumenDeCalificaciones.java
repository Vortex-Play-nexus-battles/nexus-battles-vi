package com.nexusbattles.plataforma.comentarios;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lo que la ficha de un producto ensena de sus calificaciones: promedio,
 * cuantas hay y cuantas de cada numero de estrellas — 7.1 («la calificacion
 * promedio valorada en cinco estrellas»), contrato 1.4.0.
 *
 * <p>Lo calcula el servidor a partir de la tabla de calificaciones, que desde
 * B3 es la unica fuente del promedio. Antes lo calculaba el hilo trayendo a
 * memoria todos los comentarios del producto en cada lectura; ahora es una sola
 * agregacion en la base, con el conteo por estrellas ya hecho.
 *
 * <p>Un producto sin calificaciones tiene promedio {@code null}, nunca 0: la
 * ficha tiene que poder decir «sin calificaciones» y un cero seria mentir, un
 * producto que nadie califico no es un producto de cero estrellas.
 *
 * @param productoId producto resumido
 * @param promedio con un decimal (redondeo a la mitad hacia arriba); nulo sin calificaciones
 * @param total cuantas calificaciones hay
 * @param distribucion cuantas hay de cada numero de estrellas, siempre con las cinco claves
 */
public record ResumenDeCalificaciones(
        String productoId, Double promedio, long total, Map<Integer, Long> distribucion) {

    public ResumenDeCalificaciones {
        distribucion = Collections.unmodifiableMap(new LinkedHashMap<>(distribucion));
    }

    /**
     * Construye el resumen a partir del conteo por estrellas que devuelve la base.
     *
     * <p>El promedio se divide con {@link BigDecimal} y no en coma flotante: con
     * suma y total enteros la division exacta redondeada a un decimal es la que
     * el contrato promete, y 4,25 tiene que salir 4,3 y no 4,2 por un error de
     * representacion.
     *
     * @param conteoPorEstrellas estrellas -> cuantas; las ausentes cuentan cero
     */
    public static ResumenDeCalificaciones de(String productoId, Map<Integer, Long> conteoPorEstrellas) {
        Map<Integer, Long> distribucion = new LinkedHashMap<>();
        long total = 0;
        long suma = 0;
        for (int estrellas = Calificacion.MINIMO_DE_ESTRELLAS;
                estrellas <= Calificacion.MAXIMO_DE_ESTRELLAS; estrellas++) {
            long cantidad = conteoPorEstrellas.getOrDefault(estrellas, 0L);
            distribucion.put(estrellas, cantidad);
            total += cantidad;
            suma += cantidad * estrellas;
        }
        Double promedio = total == 0
                ? null
                : BigDecimal.valueOf(suma)
                        .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                        .doubleValue();
        return new ResumenDeCalificaciones(productoId, promedio, total, distribucion);
    }

    /** El resumen de un producto que nadie ha calificado todavia. */
    public static ResumenDeCalificaciones vacio(String productoId) {
        return de(productoId, Map.of());
    }
}
