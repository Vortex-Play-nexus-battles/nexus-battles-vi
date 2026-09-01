package nexus.combate;

public final class SelectorEfecto {

    private static final int TOTAL_FILAS = 8000;

    private SelectorEfecto() {
    }

    public static CategoriaEfecto seleccionar(int indiceFila, DistribucionEfectos distribucion) {
        if (indiceFila < 1 || indiceFila > TOTAL_FILAS) {
            throw new IllegalArgumentException("El indice de fila debe estar entre 1 y " + TOTAL_FILAS);
        }

        int limite = filasDe(distribucion.causarDano());
        if (indiceFila <= limite) {
            return CategoriaEfecto.CAUSAR_DANO;
        }

        limite += filasDe(distribucion.causarDanoCritico());
        if (indiceFila <= limite) {
            return CategoriaEfecto.CAUSAR_DANO_CRITICO;
        }

        limite += filasDe(distribucion.evadirElGolpe());
        if (indiceFila <= limite) {
            return CategoriaEfecto.EVADIR_EL_GOLPE;
        }

        limite += filasDe(distribucion.resistirElGolpe());
        if (indiceFila <= limite) {
            return CategoriaEfecto.RESISTIR_EL_GOLPE;
        }

        limite += filasDe(distribucion.escaparAlGolpe());
        if (indiceFila <= limite) {
            return CategoriaEfecto.ESCAPAR_AL_GOLPE;
        }

        return CategoriaEfecto.SIN_EFECTO;
    }

    private static int filasDe(int porcentaje) {
        return porcentaje * TOTAL_FILAS / 100;
    }
}
