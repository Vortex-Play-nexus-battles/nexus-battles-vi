package nexus.combate;

import java.util.random.RandomGenerator;

public final class GeneradorIndiceTabla {

    private static final int TOTAL_FILAS = 8000;

    private GeneradorIndiceTabla() {
    }

    public static int generarIndice(RandomGenerator generador) {
        double z = generador.nextGaussian();
        double u = FuncionNormalEstandar.cdf(z);

        int indice = (int) Math.ceil(u * TOTAL_FILAS);
        if (indice < 1) {
            indice = 1;
        }
        if (indice > TOTAL_FILAS) {
            indice = TOTAL_FILAS;
        }
        return indice;
    }
}
