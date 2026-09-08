package nexus.combate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PartidaResolucionAtaqueIntegracionTest {

    @Test
    @DisplayName("el daño resuelto por HU-JUE-003 actualiza la partida y puede cerrarla")
    void aplicaResolucionConEfecto() {
        Partida partida = partidaIndividual(6);
        RandomGenerator generador = generadorFijo();
        ResolucionAtaque resolucion = ResolutorCombate.resolverCompleto(
                15,
                11,
                DistribucionEfectos.GUERRERO_ARMAS,
                6,
                generador,
                generador);

        partida.aplicarResolucionAtaque("jugador-b", resolucion);

        assertEquals(0, partida.combatiente("jugador-b").vida());
        assertFalse(partida.combatiente("jugador-b").participa());
        assertTrue(partida.finalizada());
        assertEquals(
                "jugador-a",
                partida.resultado().orElseThrow().ganadorEquipoId());
    }

    @Test
    @DisplayName("una resolución sin efecto conserva la vida y mantiene la partida activa")
    void ignoraResolucionSinEfecto() {
        Partida partida = partidaIndividual(6);
        ResolucionAtaque resolucion = ResolutorCombate.resolver(10, 10);

        partida.aplicarResolucionAtaque("jugador-b", resolucion);

        assertEquals(6, partida.combatiente("jugador-b").vida());
        assertTrue(partida.combatiente("jugador-b").participa());
        assertFalse(partida.finalizada());
    }

    private static Partida partidaIndividual(int vidaObjetivo) {
        return Partida.iniciar(List.of(
                Combatiente.nuevo("jugador-a", "jugador-a", 20),
                Combatiente.nuevo("jugador-b", "jugador-b", vidaObjetivo)));
    }

    private static RandomGenerator generadorFijo() {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0L;
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
