package nexus.combate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §6.1.4: «el indice aleatorio es una variable pseudo-aleatoria que debe seguir
 * una distribucion normal». Hasta B7 se pasaba una gaussiana por su propia
 * funcion de distribucion, lo que da un UNIFORME (transformada integral de
 * probabilidad): normal solo de nombre. Estas pruebas miden la forma de verdad,
 * con miles de tiradas y semilla fija, y tolerancias sensatas.
 */
class IndiceNormalTest {

    private static final int TIRADAS = 200_000;

    private static int[] tirar(IndiceNormal indice, long semilla) {
        RandomGenerator azar = new Random(semilla);
        int[] filas = new int[TIRADAS];
        for (int i = 0; i < TIRADAS; i++) {
            filas[i] = indice.generar(azar);
        }
        return filas;
    }

    @Test
    @DisplayName("todo indice cae dentro de la tabla de 8000 filas")
    void siempreDentroDeLaTabla() {
        for (int fila : tirar(IndiceNormal.porOmision(), 11L)) {
            assertTrue(fila >= 1 && fila <= TablaEfectos.TOTAL_FILAS, "fila fuera de la tabla: " + fila);
        }
    }

    @Test
    @DisplayName("la media y la desviacion observadas son las configuradas")
    void mediaYDesviacion() {
        int[] filas = tirar(IndiceNormal.porOmision(), 7L);
        double suma = 0;
        for (int f : filas) {
            suma += f;
        }
        double media = suma / filas.length;
        double cuadrados = 0;
        for (int f : filas) {
            cuadrados += (f - media) * (f - media);
        }
        double desviacion = Math.sqrt(cuadrados / (filas.length - 1));

        assertEquals(IndiceNormal.MEDIA_POR_OMISION, media, 15.0, "media observada");
        // La truncacion a 1..8000 recorta un 0,27 % de la masa y estrecha un
        // poco la desviacion: 2 % de tolerancia.
        assertEquals(IndiceNormal.DESVIACION_POR_OMISION, desviacion,
                IndiceNormal.DESVIACION_POR_OMISION * 0.02, "desviacion observada");
    }

    @Test
    @DisplayName("regla empirica: ~68 % a una desviacion, ~95 % a dos")
    void reglaEmpirica() {
        int[] filas = tirar(IndiceNormal.porOmision(), 3L);
        double mu = IndiceNormal.MEDIA_POR_OMISION;
        double sigma = IndiceNormal.DESVIACION_POR_OMISION;
        int unaSigma = 0;
        int dosSigmas = 0;
        for (int f : filas) {
            double z = Math.abs(f - mu) / sigma;
            if (z <= 1) {
                unaSigma++;
            }
            if (z <= 2) {
                dosSigmas++;
            }
        }
        assertEquals(0.6827, (double) unaSigma / filas.length, 0.01);
        assertEquals(0.9545, (double) dosSigmas / filas.length, 0.01);
    }

    @Test
    @DisplayName("NO es uniforme: el primer decimo de la tabla sale mucho menos que un 10 %")
    void noEsUniforme() {
        int[] filas = tirar(IndiceNormal.porOmision(), 5L);
        int enElPrimerDecimo = 0;
        int enElDecimoCentral = 0;
        for (int f : filas) {
            if (f <= 800) {
                enElPrimerDecimo++;
            }
            if (f > 3600 && f <= 4400) {
                enElDecimoCentral++;
            }
        }
        double primero = (double) enElPrimerDecimo / filas.length;
        double central = (double) enElDecimoCentral / filas.length;
        // Con un uniforme los dos serian 0,10. Con la normal: ~0,008 y ~0,235.
        assertTrue(primero < 0.02, "primer decimo: " + primero);
        assertTrue(central > 0.20, "decimo central: " + central);
    }

    @Test
    @DisplayName("la probabilidad de un tramo de filas se calcula con la normal truncada")
    void probabilidadDeUnTramo() {
        IndiceNormal indice = IndiceNormal.porOmision();
        assertEquals(1.0, indice.probabilidadDeFilas(1, 8000), 1e-9);
        assertEquals(0.5, indice.probabilidadDeFilas(1, 4000), 0.001);
        // Filas 1-4800 del Guerrero Armas (Tabla 22): Phi(0,6) en la normal.
        assertEquals(0.7264, indice.probabilidadDeFilas(1, 4800), 0.003);
        assertEquals(0.0, indice.probabilidadDeFilas(10, 9), 0.0);
    }

    @Test
    @DisplayName("la frecuencia de cada tramo coincide con su probabilidad normal")
    void frecuenciasDeTramos() {
        IndiceNormal indice = IndiceNormal.porOmision();
        int[] filas = tirar(indice, 13L);
        int[][] tramos = {{1, 3200}, {3201, 3600}, {3601, 4000}, {4001, 5600}, {5601, 8000}};
        for (int[] tramo : tramos) {
            int dentro = 0;
            for (int f : filas) {
                if (f >= tramo[0] && f <= tramo[1]) {
                    dentro++;
                }
            }
            double observada = (double) dentro / filas.length;
            double esperada = indice.probabilidadDeFilas(tramo[0], tramo[1]);
            assertEquals(esperada, observada, 0.006,
                    "tramo " + tramo[0] + "-" + tramo[1]);
        }
    }

    @Test
    @DisplayName("media y desviacion son parametros: otra media desplaza el centro")
    void parametrizable() {
        IndiceNormal indice = new IndiceNormal(2000.0, 500.0);
        int[] filas = tirar(indice, 17L);
        double suma = 0;
        for (int f : filas) {
            suma += f;
        }
        assertEquals(2000.0, suma / filas.length, 10.0);
    }

    @Test
    @DisplayName("una desviacion no positiva o una media fuera de la tabla no se aceptan")
    void parametrosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> new IndiceNormal(4000.5, 0));
        assertThrows(IllegalArgumentException.class, () -> new IndiceNormal(4000.5, -3));
        assertThrows(IllegalArgumentException.class, () -> new IndiceNormal(0.2, 100));
        assertThrows(IllegalArgumentException.class, () -> new IndiceNormal(8001, 100));
        assertThrows(IllegalArgumentException.class, () -> new IndiceNormal(Double.NaN, 100));
    }

    @Test
    @DisplayName("la misma semilla da la misma secuencia: el sorteo se puede reproducir")
    void reproducible() {
        int[] a = tirar(IndiceNormal.porOmision(), 99L);
        int[] b = tirar(IndiceNormal.porOmision(), 99L);
        for (int i = 0; i < 1000; i++) {
            assertEquals(a[i], b[i]);
        }
    }
}
