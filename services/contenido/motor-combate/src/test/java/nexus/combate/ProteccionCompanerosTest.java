package nexus.combate;

import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProteccionCompanerosTest {

    private static final int ATAQUE_RESUELTO = 15;
    private static final int DEFENSA = 11;
    private static final int DANO_RESUELTO = 6;

    @Test
    void combateCooperativo_conObjetivoDelMismoEquipo_noInfligeDano() {
        ContextoAccion contexto = new ContextoAccion(
                true, RelacionObjetivo.MISMO_EQUIPO, false);

        ResolucionAtaque resultado = resolver(contexto);

        assertInstanceOf(ResolucionAtaque.SinEfecto.class, resultado);
    }

    @Test
    void accionQuePermiteAfectarAliados_puedeAplicarDano() {
        ContextoAccion contexto = new ContextoAccion(
                true, RelacionObjetivo.MISMO_EQUIPO, true);

        ResolucionAtaque.ConEfecto resultado = assertInstanceOf(
                ResolucionAtaque.ConEfecto.class, resolver(contexto));

        assertEquals(CategoriaEfecto.CAUSAR_DANO, resultado.categoria());
        assertEquals(DANO_RESUELTO, resultado.danoAplicado());
    }

    @Test
    void combateCooperativo_conObjetivoContrario_conservaLaResolucionNormal() {
        ContextoAccion contexto = new ContextoAccion(
                true, RelacionObjetivo.EQUIPO_CONTRARIO, false);

        ResolucionAtaque.ConEfecto resultado = assertInstanceOf(
                ResolucionAtaque.ConEfecto.class, resolver(contexto));

        assertEquals(DANO_RESUELTO, resultado.danoAplicado());
    }

    @Test
    void combateNoCooperativo_noActivaLaProteccionDeCompaneros() {
        ContextoAccion contexto = new ContextoAccion(
                false, RelacionObjetivo.MISMO_EQUIPO, false);

        ResolucionAtaque.ConEfecto resultado = assertInstanceOf(
                ResolucionAtaque.ConEfecto.class, resolver(contexto));

        assertEquals(DANO_RESUELTO, resultado.danoAplicado());
    }

    @Test
    void contextoExigeUnaRelacionConElObjetivo() {
        assertThrows(NullPointerException.class,
                () -> new ContextoAccion(true, null, false));
    }

    @Test
    void resolucionExigeUnContextoDeAccion() {
        RandomGenerator generador = generadorFijoEnCero();

        assertThrows(NullPointerException.class, () -> ResolutorCombate.resolverCompleto(
                null,
                ATAQUE_RESUELTO,
                DEFENSA,
                DistribucionEfectos.GUERRERO_ARMAS,
                DANO_RESUELTO,
                generador,
                generador));
    }

    private static ResolucionAtaque resolver(ContextoAccion contexto) {
        RandomGenerator generador = generadorFijoEnCero();
        return ResolutorCombate.resolverCompleto(
                contexto,
                ATAQUE_RESUELTO,
                DEFENSA,
                DistribucionEfectos.GUERRERO_ARMAS,
                DANO_RESUELTO,
                generador,
                generador);
    }

    private static RandomGenerator generadorFijoEnCero() {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return Double.doubleToLongBits(0.0);
            }

            @Override
            public double nextGaussian() {
                return 0.0;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }
}
