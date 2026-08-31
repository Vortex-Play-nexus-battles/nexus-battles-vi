package nexus.combate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PartidaFinTest {

    @Test
    @DisplayName("un héroe con cero puntos de vida fallece y deja de participar")
    void declaraFallecidoAlLlegarACero() {
        Partida partida = individual(100, 100);

        partida.aplicarDanio("jugador-a", 100);

        assertFalse(partida.combatiente("jugador-a").participa());
        assertEquals(0, partida.combatiente("jugador-a").vida());
        assertTrue(partida.finalizada());
        assertEquals("jugador-b", partida.resultado().orElseThrow().ganadorEquipoId());
        assertEquals(MotivoFinPartida.SUPERVIVENCIA,
                partida.resultado().orElseThrow().motivo());
    }

    @Test
    @DisplayName("un combatiente no puede construirse con vida negativa o participar sin vida")
    void rechazaEstadosInvalidosDeCombatiente() {
        assertThrows(IllegalArgumentException.class,
                () -> new Combatiente("jugador-a", "equipo-a", -1, false));
        assertThrows(IllegalArgumentException.class,
                () -> new Combatiente("jugador-a", "equipo-a", 0, true));
    }

    @Test
    @DisplayName("el combate cooperativo termina solo cuando queda un equipo activo")
    void terminaCuandoQuedaUnSoloEquipo() {
        Partida partida = Partida.iniciar(
                List.of(
                        Combatiente.nuevo("a-1", "equipo-a", 50),
                        Combatiente.nuevo("a-2", "equipo-a", 50),
                        Combatiente.nuevo("b-1", "equipo-b", 50)));

        partida.aplicarDanio("a-1", 50);
        assertFalse(partida.finalizada());

        partida.aplicarDanio("a-2", 70);

        assertTrue(partida.finalizada());
        assertEquals("equipo-b", partida.resultado().orElseThrow().ganadorEquipoId());
    }

    @Test
    @DisplayName("una partida finalizada rechaza cualquier acción posterior")
    void rechazaAccionDespuesDelFinal() {
        Partida partida = individual(10, 10);
        partida.aplicarDanio("jugador-b", 10);

        assertThrows(PartidaFinalizadaException.class,
                () -> partida.aplicarDanio("jugador-a", 1));
        assertEquals(10, partida.combatiente("jugador-a").vida());
    }

    @Test
    @DisplayName("todos los cierres notifican el resultado una sola vez")
    void notificaCierrePorSupervivencia() {
        List<ResultadoPartida> cierres = new ArrayList<>();
        Partida partida = Partida.iniciar(
                List.of(
                        Combatiente.nuevo("jugador-a", "jugador-a", 20),
                        Combatiente.nuevo("jugador-b", "jugador-b", 20)),
                cierres::add);

        partida.aplicarDanio("jugador-b", 20);

        assertEquals(1, cierres.size());
        assertEquals(partida.resultado().orElseThrow(), cierres.getFirst());
    }

    private static Partida individual(int vidaA, int vidaB) {
        return Partida.iniciar(
                List.of(
                        Combatiente.nuevo("jugador-a", "jugador-a", vidaA),
                        Combatiente.nuevo("jugador-b", "jugador-b", vidaB)));
    }
}
