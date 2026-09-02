package nexus.combate;

import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ResolutorCombateCriterioDosTest {

    @Test
    void ataqueResueltoMayorQueDefensa_produceConEfecto() {
        RandomGenerator generadorFijoEnCero = generadorFijoEnGaussiano(0.0);

        ResolucionAtaque resultado = ResolutorCombate.resolverCompleto(
            15, 11,
            DistribucionEfectos.GUERRERO_ARMAS,
            6,
            generadorFijoEnCero,
            generadorFijoEnCero);

        assertInstanceOf(ResolucionAtaque.ConEfecto.class, resultado);
    }

    @Test
    void gaussianoEnCero_caeEnElCentroDeLaDistribucion_causarDanoParaGuerreroArmas() {
        RandomGenerator generadorFijoEnCero = generadorFijoEnGaussiano(0.0);

        ResolucionAtaque.ConEfecto resultado = (ResolucionAtaque.ConEfecto) ResolutorCombate.resolverCompleto(
            15, 11,
            DistribucionEfectos.GUERRERO_ARMAS,
            6,
            generadorFijoEnCero,
            generadorFijoEnCero);

        assertEquals(CategoriaEfecto.CAUSAR_DANO, resultado.categoria());
        assertEquals(6, resultado.danoAplicado());
    }

    @Test
    void categoriaSinEfecto_noAplicaDano() {
        RandomGenerator generadorFijoEnTresPuntoCinco = generadorFijoEnGaussiano(3.5);

        ResolucionAtaque.ConEfecto resultado = (ResolucionAtaque.ConEfecto) ResolutorCombate.resolverCompleto(
            15, 11,
            DistribucionEfectos.GUERRERO_ARMAS,
            6,
            generadorFijoEnTresPuntoCinco,
            generadorFijoEnTresPuntoCinco);

        assertEquals(CategoriaEfecto.SIN_EFECTO, resultado.categoria());
        assertEquals(0, resultado.danoAplicado());
    }

    private static RandomGenerator generadorFijoEnGaussiano(double valor) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return Double.doubleToLongBits(valor);
            }

            @Override
            public double nextGaussian() {
                return valor;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }
}
