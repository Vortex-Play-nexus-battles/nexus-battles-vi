package nexus.combate;

import java.util.Objects;

public record EfectoEquipamiento(
        CategoriaEfecto categoria,
        int puntosPorcentuales) {

    public EfectoEquipamiento {
        Objects.requireNonNull(categoria, "categoria no puede ser nula");
        if (categoria == CategoriaEfecto.SIN_EFECTO) {
            throw new IllegalArgumentException("El equipamiento debe incrementar un efecto real");
        }
        if (puntosPorcentuales <= 0) {
            throw new IllegalArgumentException("El incremento debe ser mayor que cero");
        }
    }
}
