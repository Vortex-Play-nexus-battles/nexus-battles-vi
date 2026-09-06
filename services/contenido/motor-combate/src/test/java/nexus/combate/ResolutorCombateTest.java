package nexus.combate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ResolutorCombateTest {

    @Test
    void ataqueResueltoIgualQueDefensa_noProduceEfecto() {
        DetalleAtaque ataqueGuerreroTanque = new DetalleAtaque(10, 1, 6);
        RandomGenerator dadoMinimo = fijarTiradaEn(0);
        int ataqueResuelto = TiradorDados.resolver(ataqueGuerreroTanque, dadoMinimo);

        ResolucionAtaque resultado = ResolutorCombate.resolver(ataqueResuelto, 11);

        assertInstanceOf(ResolucionAtaque.SinEfecto.class, resultado);
    }

    @ParameterizedTest
    @CsvSource({
        "10, 10",
        "5, 10",
        "0, 1"
    })
    void ataqueResueltoMenorOIgualQueDefensa_siempreSinEfecto(int ataqueResuelto, int defensa) {
        ResolucionAtaque resultado = ResolutorCombate.resolver(ataqueResuelto, defensa);

        assertInstanceOf(ResolucionAtaque.SinEfecto.class, resultado);
    }

    private static RandomGenerator fijarTiradaEn(int valor) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return valor;
            }

            @Override
            public int nextInt(int bound) {
                return valor;
            }
        };
    }
}
