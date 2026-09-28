package nexus.inventario.dominio;

import java.util.List;
import java.util.Objects;

/**
 * La experiencia para subir de cada nivel (seccion 6.1.1: 100 x 1,2^(n-1), tope
 * 8), tal como la publica el servicio de heroes en
 * {@code GET /api/v1/progresion/niveles}. El inventario no la reimplementa: la
 * lee y la aplica al guardar la experiencia que trae una mision (1.6.0, B9).
 *
 * @param paraSubir experiencia para pasar del nivel {@code i + 1} al siguiente;
 *                  tantas entradas como niveles tienen techo (7 para el tope 8)
 */
public record TablaDeNiveles(List<Double> paraSubir) {

    public TablaDeNiveles {
        paraSubir = List.copyOf(Objects.requireNonNull(paraSubir, "paraSubir"));
        if (paraSubir.isEmpty()) {
            throw new IllegalArgumentException("Una tabla de niveles sin niveles no sirve para progresar.");
        }
        for (Double puntos : paraSubir) {
            if (puntos == null || !(puntos > 0)) {
                throw new IllegalArgumentException("La experiencia para subir de nivel debe ser positiva.");
            }
        }
    }

    /** El nivel mas alto: el que no tiene experiencia para subir. */
    public int nivelMaximo() {
        return paraSubir.size() + 1;
    }

    /**
     * Suma experiencia a un heroe: sube los niveles que alcance, conserva el
     * sobrante y en el nivel maximo la acumula sin subir.
     */
    public Progresion sumar(int nivel, double experiencia, double puntos) {
        if (puntos < 0) {
            throw new IllegalArgumentException("La experiencia ganada no puede ser negativa.");
        }
        int nivelActual = Math.max(1, Math.min(nivel, nivelMaximo()));
        double acumulada = experiencia + puntos;
        while (nivelActual < nivelMaximo() && acumulada >= paraSubir.get(nivelActual - 1)) {
            acumulada -= paraSubir.get(nivelActual - 1);
            nivelActual++;
        }
        return new Progresion(nivelActual, acumulada);
    }

    public record Progresion(int nivel, double experiencia) {
    }
}
