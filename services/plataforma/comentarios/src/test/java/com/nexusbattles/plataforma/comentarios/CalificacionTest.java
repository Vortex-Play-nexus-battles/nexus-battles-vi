package com.nexusbattles.plataforma.comentarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La calificacion y su resumen — 7.1 del documento del curso («la calificacion
 * promedio valorada en cinco estrellas»; «solo pueden calificar un producto una
 * vez»), contrato 1.4.0.
 */
class CalificacionTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T10:00:00Z");

    @Test
    @DisplayName("la escala es de 1 a 5 estrellas y las estrellas son obligatorias")
    void escala() {
        assertThrows(IllegalArgumentException.class, () -> Calificacion.exigirEstrellas(null));
        assertThrows(IllegalArgumentException.class, () -> Calificacion.exigirEstrellas(0));
        assertThrows(IllegalArgumentException.class, () -> Calificacion.exigirEstrellas(6));
        assertEquals(1, Calificacion.exigirEstrellas(1));
        assertEquals(5, Calificacion.exigirEstrellas(5));
        assertThrows(IllegalArgumentException.class, () -> new Calificacion("c", "p", "a", 7, AHORA));
        assertThrows(IllegalArgumentException.class, () -> new Calificacion("c", " ", "a", 3, AHORA));
        assertThrows(NullPointerException.class, () -> new Calificacion("c", "p", "a", 3, null));
    }

    @Test
    @DisplayName("sin calificaciones: promedio nulo (no cero), total 0 y las cinco claves a cero")
    void sinCalificaciones() {
        ResumenDeCalificaciones resumen = ResumenDeCalificaciones.vacio("p-1");

        assertNull(resumen.promedio(), "un producto que nadie califico no es de cero estrellas");
        assertEquals(0, resumen.total());
        assertEquals(List.of(1, 2, 3, 4, 5), List.copyOf(resumen.distribucion().keySet()));
        resumen.distribucion().values().forEach(cantidad -> assertEquals(0L, cantidad));
    }

    @Test
    @DisplayName("el promedio va con un decimal, redondeando la mitad hacia arriba y sin error de coma flotante")
    void promedioConUnDecimal() {
        // 5+4+4+4 = 17/4 = 4,25 -> 4,3 (con doubles saldria 4,2 por representacion)
        ResumenDeCalificaciones resumen = ResumenDeCalificaciones.de("p-1", Map.of(5, 1L, 4, 3L));
        assertEquals(4.3, resumen.promedio());
        assertEquals(4, resumen.total());
        assertEquals(3L, resumen.distribucion().get(4));
        assertEquals(0L, resumen.distribucion().get(1));

        // 1+2+2 = 5/3 = 1,666... -> 1,7
        assertEquals(1.7, ResumenDeCalificaciones.de("p-1", Map.of(1, 1L, 2, 2L)).promedio());
        // exacto
        assertEquals(3.0, ResumenDeCalificaciones.de("p-1", Map.of(3, 7L)).promedio());
    }

    @Test
    @DisplayName("un conteo con valores fuera de la escala no entra en el resumen")
    void fueraDeLaEscala() {
        ResumenDeCalificaciones resumen = ResumenDeCalificaciones.de("p-1", Map.of(9, 4L, 2, 1L));
        assertEquals(1, resumen.total());
        assertEquals(2.0, resumen.promedio());
        assertEquals(5, resumen.distribucion().size());
    }
}
