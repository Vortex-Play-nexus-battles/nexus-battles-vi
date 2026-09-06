package nexus.combate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

/** Pruebas de HU-JUE-002 - Control de una accion por turno. */
class ControlAccionesTurnoTest {

    private static final Duration DURACION = Duration.ofSeconds(30);
    private static final Instant INSTANTE_INICIAL =
            Instant.parse("2026-09-01T14:30:00Z");

    @Test
    @DisplayName("una accion resuelta entrega el turno al siguiente participante")
    void unaAccionResueltaAvanzaAlSiguienteParticipante() {
        RelojMutable reloj = new RelojMutable(INSTANTE_INICIAL);
        ControlAccionesTurno control = crearControl(reloj);
        String participanteInicial = control.participanteActivo();

        control.ejecutarAccion(participanteInicial);

        assertNotEquals(participanteInicial, control.participanteActivo());
        assertThrows(IllegalStateException.class,
                () -> control.ejecutarAccion(participanteInicial));
    }

    @Test
    @DisplayName("un participante puede volver a actuar al llegar su siguiente turno")
    void participanteVuelveAActuarEnSuSiguienteTurno() {
        RelojMutable reloj = new RelojMutable(INSTANTE_INICIAL);
        ControlAccionesTurno control = crearControl(reloj);
        String primero = control.participanteActivo();

        control.ejecutarAccion(primero);
        String segundo = control.participanteActivo();
        control.ejecutarAccion(segundo);

        assertEquals(primero, control.participanteActivo());
        control.ejecutarAccion(primero);
        assertEquals(segundo, control.participanteActivo());
    }

    @Test
    @DisplayName("todos los participantes reciben exactamente la misma duracion")
    void todosLosTurnosUsanLaMismaDuracion() {
        RelojMutable reloj = new RelojMutable(INSTANTE_INICIAL);
        ControlAccionesTurno control = crearControl(reloj);

        assertEquals(DURACION,
                Duration.between(control.inicioTurno(), control.limiteTurno()));

        reloj.avanzar(Duration.ofSeconds(7));
        control.ejecutarAccion(control.participanteActivo());

        assertEquals(DURACION,
                Duration.between(control.inicioTurno(), control.limiteTurno()));
        assertEquals(reloj.instant(), control.inicioTurno());

        reloj.avanzar(DURACION);
        assertTrue(control.expirarSiCorresponde());
        assertEquals(DURACION,
                Duration.between(control.inicioTurno(), control.limiteTurno()));
    }

    @Test
    @DisplayName("el turno no expira antes del limite y avanza al agotarse")
    void turnoExpiraSinAccionYAvanza() {
        RelojMutable reloj = new RelojMutable(INSTANTE_INICIAL);
        ControlAccionesTurno control = crearControl(reloj);
        String participanteInicial = control.participanteActivo();

        reloj.avanzar(DURACION.minusMillis(1));
        assertFalse(control.expirarSiCorresponde());
        assertEquals(participanteInicial, control.participanteActivo());

        reloj.avanzar(Duration.ofMillis(1));
        assertTrue(control.expirarSiCorresponde());
        assertNotEquals(participanteInicial, control.participanteActivo());
    }

    @Test
    @DisplayName("una accion posterior al vencimiento se rechaza y el turno avanza")
    void accionPosteriorAlVencimientoEsRechazada() {
        RelojMutable reloj = new RelojMutable(INSTANTE_INICIAL);
        ControlAccionesTurno control = crearControl(reloj);
        String participanteVencido = control.participanteActivo();

        reloj.avanzar(DURACION);

        assertThrows(IllegalStateException.class,
                () -> control.ejecutarAccion(participanteVencido));
        assertNotEquals(participanteVencido, control.participanteActivo());
    }

    @Test
    @DisplayName("la duracion del turno es obligatoria y mayor que cero")
    void rechazaDuracionInvalida() {
        ColaTurnos cola = ColaTurnos.sortear(
                List.of("ana", "bruno"), new Random(29));
        RelojMutable reloj = new RelojMutable(INSTANTE_INICIAL);

        assertThrows(IllegalArgumentException.class,
                () -> ControlAccionesTurno.iniciar(cola, Duration.ZERO, reloj));
        assertThrows(IllegalArgumentException.class,
                () -> ControlAccionesTurno.iniciar(
                        cola, Duration.ofSeconds(-1), reloj));
        assertThrows(IllegalArgumentException.class,
                () -> ControlAccionesTurno.iniciar(cola, null, reloj));
    }

    private static ControlAccionesTurno crearControl(RelojMutable reloj) {
        ColaTurnos cola = ColaTurnos.sortear(
                List.of("ana", "bruno"), new Random(29));
        return ControlAccionesTurno.iniciar(cola, DURACION, reloj);
    }

    private static final class RelojMutable extends Clock {

        private Instant instante;
        private final ZoneId zona;

        private RelojMutable(Instant instante) {
            this(instante, ZoneOffset.UTC);
        }

        private RelojMutable(Instant instante, ZoneId zona) {
            this.instante = instante;
            this.zona = zona;
        }

        @Override
        public ZoneId getZone() {
            return zona;
        }

        @Override
        public Clock withZone(ZoneId nuevaZona) {
            return new RelojMutable(instante, nuevaZona);
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
