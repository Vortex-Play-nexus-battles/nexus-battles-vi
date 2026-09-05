package nexus.combate;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TablaEfectos {

    public static final int TOTAL_FILAS = 8_000;

    private static final List<CategoriaEfecto> ORDEN = List.of(
            CategoriaEfecto.CAUSAR_DANO,
            CategoriaEfecto.CAUSAR_DANO_CRITICO,
            CategoriaEfecto.EVADIR_EL_GOLPE,
            CategoriaEfecto.RESISTIR_EL_GOLPE,
            CategoriaEfecto.ESCAPAR_AL_GOLPE,
            CategoriaEfecto.SIN_EFECTO);

    private final Map<CategoriaEfecto, Integer> filasPorCategoria;

    private TablaEfectos(Map<CategoriaEfecto, Integer> filasPorCategoria) {
        this.filasPorCategoria = Map.copyOf(filasPorCategoria);
        if (totalFilas() != TOTAL_FILAS) {
            throw new IllegalArgumentException("La tabla de efectos debe conservar 8000 filas");
        }
    }

    public static TablaEfectos desde(DistribucionEfectos distribucion) {
        Objects.requireNonNull(distribucion, "distribucion no puede ser nula");
        EnumMap<CategoriaEfecto, Integer> filas = new EnumMap<>(CategoriaEfecto.class);
        filas.put(CategoriaEfecto.CAUSAR_DANO, filasDe(distribucion.causarDano()));
        filas.put(CategoriaEfecto.CAUSAR_DANO_CRITICO, filasDe(distribucion.causarDanoCritico()));
        filas.put(CategoriaEfecto.EVADIR_EL_GOLPE, filasDe(distribucion.evadirElGolpe()));
        filas.put(CategoriaEfecto.RESISTIR_EL_GOLPE, filasDe(distribucion.resistirElGolpe()));
        filas.put(CategoriaEfecto.ESCAPAR_AL_GOLPE, filasDe(distribucion.escaparAlGolpe()));
        filas.put(CategoriaEfecto.SIN_EFECTO, filasDe(distribucion.sinEfecto()));
        return new TablaEfectos(filas);
    }

    public CategoriaEfecto efectoEn(int indiceFila) {
        if (indiceFila < 1 || indiceFila > TOTAL_FILAS) {
            throw new IllegalArgumentException("El indice de fila debe estar entre 1 y 8000");
        }
        int limite = 0;
        for (CategoriaEfecto categoria : ORDEN) {
            limite += filasDe(categoria);
            if (indiceFila <= limite) {
                return categoria;
            }
        }
        throw new IllegalStateException("La tabla no contiene la fila solicitada");
    }

    public int filasDe(CategoriaEfecto categoria) {
        Objects.requireNonNull(categoria, "categoria no puede ser nula");
        return filasPorCategoria.get(categoria);
    }

    public int totalFilas() {
        return filasPorCategoria.values().stream().mapToInt(Integer::intValue).sum();
    }

    public List<CategoriaEfecto> orden() {
        return ORDEN;
    }

    private static int filasDe(int porcentaje) {
        return porcentaje * TOTAL_FILAS / 100;
    }
}
