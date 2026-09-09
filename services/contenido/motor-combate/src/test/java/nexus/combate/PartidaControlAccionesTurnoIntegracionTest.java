package nexus.combate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PartidaControlAccionesTurnoIntegracionTest {

    private static final Instant INICIO = Instant.parse("2026-09-01T18:00:00Z");

    @Test
    @DisplayName("una acción válida actualiza la partida y entrega el turno")
    void ejecutaAtaqueYAvanzaTurno() {
        RelojMutable reloj = new RelojMutable(INICIO);
        ControlAccionesTurno control = control(
                List.of("jugador-a", "jugador-b"),
                reloj);
        Partida partida = Partida.iniciar(List.of(
                Combatiente.nuevo("jugador-a", "equipo-a", 10),
                Combatiente.nuevo("jugador-b", "equipo-b", 10)));
        String atacante = control.participanteActivo();
        String objetivo = atacante.equals("jugador-a") ? "jugador-b" : "jugador-a";
        ResolucionAtaque resolucion = new ResolucionAtaque.ConEfecto(
                CategoriaEfecto.CAUSAR_DANO,
                4000,
                3);

        partida.ejecutarAtaque(control, atacante, objetivo, resolucion);

        assertEquals(7, partida.combatiente(objetivo).vida());
        assertNotEquals(atacante, control.participanteActivo());
        assertFalse(partida.finalizada());
    }

    @Test
    @DisplayName("la expiración de HU-JUE-002 elimina al equipo inactivo en HU-JUE-007")
    void pierdeEquipoAlExpirarTurno() {
        RelojMutable reloj = new RelojMutable(INICIO);
        ControlAccionesTurno control = control(
                List.of("a-1", "a-2", "b-1"),
                reloj);
        Partida partida = Partida.iniciar(
                List.of(
                        Combatiente.nuevo("a-1", "equipo-a", 10),
                        Combatiente.nuevo("a-2", "equipo-a", 10),
                        Combatiente.nuevo("b-1", "equipo-b", 10)),
                INICIO,
                empatados -> empatados.getFirst().equipoId());
        String participanteInactivo = control.participanteActivo();
        String equipoInactivo = partida.combatiente(participanteInactivo).equipoId();

        reloj.avanzar(Duration.ofMinutes(1));
        partida.evaluarLimites(control, reloj.instant());

        String equipoGanador = equipoInactivo.equals("equipo-a")
                ? "equipo-b"
                : "equipo-a";
        assertTrue(partida.finalizada());
        assertEquals(
                MotivoFinPartida.INACTIVIDAD,
                partida.resultado().orElseThrow().motivo());
        assertEquals(
                equipoGanador,
                partida.resultado().orElseThrow().ganadorEquipoId());
        assertTrue(List.of("a-1", "a-2", "b-1").stream()
                .filter(id -> partida.combatiente(id).equipoId().equals(equipoInactivo))
                .noneMatch(id -> partida.combatiente(id).participa()));
    }

    private static ControlAccionesTurno control(
            List<String> participantes,
            RelojMutable reloj) {
        ColaTurnos cola = ColaTurnos.sortear(participantes, new Random(29));
        return ControlAccionesTurno.iniciar(
                cola,
                Duration.ofMinutes(1),
                reloj);
    }

    private static final class RelojMutable extends Clock {

        private Instant instante;

        private RelojMutable(Instant instante) {
            this.instante = instante;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return instante;
        }

        private void avanzar(Duration duracion) {
            instante = instante.plus(duracion);
        }
    }
}
