package nexus.combate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LimitesTemporalesPartidaTest {

    private static final Instant INICIO = Instant.parse("2026-08-26T15:00:00Z");

    @Test
    @DisplayName("a los seis minutos gana el equipo que conserva más vida")
    void terminaPorTiempoConMayorVida() {
        Partida partida = Partida.iniciar(
                List.of(
                        Combatiente.nuevo("jugador-a", "equipo-a", 80),
                        Combatiente.nuevo("jugador-b", "equipo-b", 50)),
                INICIO,
                empatados -> empatados.getFirst().equipoId());

        partida.evaluarLimites(INICIO.plus(Duration.ofMinutes(6)));

        assertTrue(partida.finalizada());
        assertEquals("equipo-a", partida.resultado().orElseThrow().ganadorEquipoId());
        assertEquals(MotivoFinPartida.TIEMPO_MAXIMO,
                partida.resultado().orElseThrow().motivo());
    }

    @Test
    @DisplayName("antes de los topes la partida continúa")
    void noTerminaAntesDeLosTopes() {
        Partida partida = individualConVidas(100, 100, empatados -> "jugador-a");
        partida.iniciarTurno("jugador-a", INICIO.plusSeconds(1));

        partida.evaluarLimites(INICIO.plusSeconds(59));

        assertFalse(partida.finalizada());
    }

    @Test
    @DisplayName("un minuto de inactividad elimina al equipo completo")
    void inactividadHacePerderATodoElEquipo() {
        Partida partida = Partida.iniciar(
                List.of(
                        Combatiente.nuevo("a-1", "equipo-a", 40),
                        Combatiente.nuevo("a-2", "equipo-a", 40),
                        Combatiente.nuevo("b-1", "equipo-b", 20)),
                INICIO,
                empatados -> empatados.getFirst().equipoId());
        partida.iniciarTurno("a-1", INICIO);

        partida.evaluarLimites(INICIO.plus(Duration.ofMinutes(1)));

        assertFalse(partida.combatiente("a-1").participa());
        assertFalse(partida.combatiente("a-2").participa());
        assertTrue(partida.finalizada());
        assertEquals("equipo-b", partida.resultado().orElseThrow().ganadorEquipoId());
        assertEquals(MotivoFinPartida.INACTIVIDAD,
                partida.resultado().orElseThrow().motivo());
    }

    @Test
    @DisplayName("un empate exacto usa el criterio documentado inyectado")
    void desempataConLaEstrategiaConfigurada() {
        AtomicBoolean criterioInvocado = new AtomicBoolean();
        List<ResultadoPartida> cierres = new ArrayList<>();
        CriterioDesempate criterio = empatados -> {
            criterioInvocado.set(true);
            assertEquals(2, empatados.size());
            return "jugador-b";
        };
        Partida partida = Partida.iniciar(
                List.of(
                        Combatiente.nuevo("jugador-a", "jugador-a", 75),
                        Combatiente.nuevo("jugador-b", "jugador-b", 75)),
                INICIO,
                criterio,
                cierres::add);

        partida.evaluarLimites(INICIO.plus(Duration.ofMinutes(6)));

        assertTrue(criterioInvocado.get());
        assertEquals("jugador-b", partida.resultado().orElseThrow().ganadorEquipoId());
        assertEquals(1, cierres.size());
        assertEquals(MotivoFinPartida.TIEMPO_MAXIMO, cierres.getFirst().motivo());
    }

    @Test
    @DisplayName("la actividad reinicia el plazo del turno para el participante activo")
    void actividadReiniciaPlazoDeInactividad() {
        Partida partida = individualConVidas(100, 100, empatados -> "jugador-a");
        partida.iniciarTurno("jugador-a", INICIO);
        partida.registrarActividad("jugador-a", INICIO.plusSeconds(40));

        partida.evaluarLimites(INICIO.plusSeconds(90));

        assertFalse(partida.finalizada());
        assertTrue(partida.combatiente("jugador-a").participa());
    }

    @Test
    @DisplayName("si inactividad y tiempo coinciden se eliminan inactivos y se cierra por tiempo")
    void resuelveInactividadAntesDelCierrePorTiempo() {
        Partida partida = Partida.iniciar(
                List.of(
                        Combatiente.nuevo("jugador-a", "equipo-a", 100),
                        Combatiente.nuevo("jugador-b", "equipo-b", 80),
                        Combatiente.nuevo("jugador-c", "equipo-c", 60)),
                INICIO,
                empatados -> empatados.getFirst().equipoId());
        partida.iniciarTurno("jugador-a", INICIO.plus(Duration.ofMinutes(5)));

        partida.evaluarLimites(INICIO.plus(Duration.ofMinutes(6)));

        assertFalse(partida.combatiente("jugador-a").participa());
        assertTrue(partida.finalizada());
        assertEquals("equipo-b", partida.resultado().orElseThrow().ganadorEquipoId());
        assertEquals(MotivoFinPartida.TIEMPO_MAXIMO,
                partida.resultado().orElseThrow().motivo());
    }

    private static Partida individualConVidas(
            int vidaA,
            int vidaB,
            CriterioDesempate criterio) {
        return Partida.iniciar(
                List.of(
                        Combatiente.nuevo("jugador-a", "jugador-a", vidaA),
                        Combatiente.nuevo("jugador-b", "jugador-b", vidaB)),
                INICIO,
                criterio);
    }
}
