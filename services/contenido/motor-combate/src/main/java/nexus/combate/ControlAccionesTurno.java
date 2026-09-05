package nexus.combate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Controla que cada participante ejecute como maximo una accion en su turno.
 *
 * <p>La duracion se configura una sola vez para el combate y se reutiliza al
 * iniciar cada turno. Resolver una accion o agotar el tiempo avanza sobre la
 * secuencia inmutable definida por {@link ColaTurnos}.</p>
 */
public final class ControlAccionesTurno {

    private final ColaTurnos colaTurnos;
    private final Duration duracionTurno;
    private final Clock reloj;
    private Instant inicioTurno;

    private ControlAccionesTurno(
            ColaTurnos colaTurnos,
            Duration duracionTurno,
            Clock reloj) {
        this.colaTurnos = Objects.requireNonNull(
                colaTurnos, "La cola de turnos es obligatoria");
        this.duracionTurno = validarDuracion(duracionTurno);
        this.reloj = Objects.requireNonNull(reloj, "El reloj es obligatorio");
        this.inicioTurno = reloj.instant();
    }

    /** Sortea el orden inicial y comienza el primer turno. */
    public static ControlAccionesTurno iniciar(
            List<String> participantes,
            Duration duracionTurno) {
        return iniciar(
                ColaTurnos.sortear(participantes),
                duracionTurno,
                Clock.systemUTC());
    }

    /** Comienza el control sobre una cola previamente sorteada. */
    public static ControlAccionesTurno iniciar(
            ColaTurnos colaTurnos,
            Duration duracionTurno) {
        return iniciar(colaTurnos, duracionTurno, Clock.systemUTC());
    }

    /** Variante con reloj inyectado para pruebas reproducibles. */
    static ControlAccionesTurno iniciar(
            ColaTurnos colaTurnos,
            Duration duracionTurno,
            Clock reloj) {
        return new ControlAccionesTurno(colaTurnos, duracionTurno, reloj);
    }

    /** Devuelve el participante que puede actuar en este momento. */
    public synchronized String participanteActivo() {
        return colaTurnos.participanteActivo();
    }

    /** Devuelve la duracion comun aplicada a todos los turnos. */
    public Duration duracionTurno() {
        return duracionTurno;
    }

    /** Devuelve el instante en que comenzo el turno actual. */
    public synchronized Instant inicioTurno() {
        return inicioTurno;
    }

    /** Devuelve el instante limite del turno actual. */
    public synchronized Instant limiteTurno() {
        return inicioTurno.plus(duracionTurno);
    }

    /**
     * Registra una accion resuelta y entrega el turno al siguiente participante.
     *
     * @param participante identificador de quien intenta ejecutar la accion
     * @throws IllegalStateException si el turno expiro o pertenece a otro
     *         participante
     */
    public synchronized void ejecutarAccion(String participante) {
        if (expirarSiCorresponde()) {
            throw new IllegalStateException(
                    "El turno expiro antes de ejecutar la accion");
        }
        if (!Objects.equals(participante, colaTurnos.participanteActivo())) {
            throw new IllegalStateException(
                    "El participante no tiene el turno activo");
        }
        avanzarTurno(reloj.instant());
    }

    /**
     * Avanza sin accion cuando se alcanza la duracion limite del turno.
     *
     * @return {@code true} cuando el turno expiro y se realizo el avance
     */
    public synchronized boolean expirarSiCorresponde() {
        Instant ahora = reloj.instant();
        if (ahora.isBefore(limiteTurno())) {
            return false;
        }
        avanzarTurno(ahora);
        return true;
    }

    private void avanzarTurno(Instant nuevoInicio) {
        colaTurnos.avanzar();
        inicioTurno = nuevoInicio;
    }

    private static Duration validarDuracion(Duration duracionTurno) {
        if (duracionTurno == null
                || duracionTurno.isZero()
                || duracionTurno.isNegative()) {
            throw new IllegalArgumentException(
                    "La duracion del turno debe ser mayor que cero");
        }
        return duracionTurno;
    }
}
