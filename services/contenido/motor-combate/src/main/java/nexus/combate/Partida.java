package nexus.combate;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Estado de una partida y sus condiciones de cierre. */
public final class Partida {

    public static final Duration DURACION_MAXIMA = Duration.ofMinutes(6);
    public static final Duration INACTIVIDAD_MAXIMA = Duration.ofMinutes(1);

    private static final AlCerrarPartida SIN_REACCION_DE_CIERRE = resultado -> { };
    private static final CriterioDesempate CRITERIO_NO_CONFIGURADO = empatados -> {
        throw new IllegalStateException("El criterio de desempate no está configurado");
    };

    private final Map<String, Combatiente> combatientes;
    private final Instant iniciadaEn;
    private final CriterioDesempate criterioDesempate;
    private final AlCerrarPartida alCerrarPartida;

    private ResultadoPartida resultado;
    private String participanteEnTurno;
    private Instant ultimaActividadDelTurno;

    private Partida(
            List<Combatiente> participantes,
            Instant iniciadaEn,
            CriterioDesempate criterioDesempate,
            AlCerrarPartida alCerrarPartida) {
        if (participantes == null || participantes.size() < 2) {
            throw new IllegalArgumentException("La partida requiere al menos dos combatientes");
        }
        this.combatientes = participantes.stream().collect(Collectors.toMap(
                Combatiente::id,
                Function.identity(),
                (primero, repetido) -> {
                    throw new IllegalArgumentException(
                            "Los identificadores de combatientes no se pueden repetir");
                },
                LinkedHashMap::new));
        if (equiposActivos().size() < 2) {
            throw new IllegalArgumentException("La partida requiere al menos dos equipos");
        }
        this.iniciadaEn = Objects.requireNonNull(iniciadaEn, "La fecha de inicio es obligatoria");
        this.criterioDesempate = Objects.requireNonNull(
                criterioDesempate,
                "El criterio de desempate es obligatorio");
        this.alCerrarPartida = Objects.requireNonNull(
                alCerrarPartida,
                "El procesador de cierre es obligatorio");
    }

    /** Inicio simplificado para cierres por supervivencia. */
    public static Partida iniciar(List<Combatiente> participantes) {
        return iniciar(
                participantes,
                Instant.now(),
                CRITERIO_NO_CONFIGURADO,
                SIN_REACCION_DE_CIERRE);
    }

    /** Inicio simplificado para cierres por supervivencia con notificación. */
    public static Partida iniciar(
            List<Combatiente> participantes,
            AlCerrarPartida alCerrarPartida) {
        return iniciar(
                participantes,
                Instant.now(),
                CRITERIO_NO_CONFIGURADO,
                alCerrarPartida);
    }

    public static Partida iniciar(
            List<Combatiente> participantes,
            Instant iniciadaEn,
            CriterioDesempate criterioDesempate) {
        return iniciar(participantes, iniciadaEn, criterioDesempate, SIN_REACCION_DE_CIERRE);
    }

    public static Partida iniciar(
            List<Combatiente> participantes,
            Instant iniciadaEn,
            CriterioDesempate criterioDesempate,
            AlCerrarPartida alCerrarPartida) {
        return new Partida(participantes, iniciadaEn, criterioDesempate, alCerrarPartida);
    }

    public void aplicarDanio(String combatienteId, int danio) {
        exigirEnCurso();
        Combatiente actual = combatiente(combatienteId);
        if (!actual.participa()) {
            throw new IllegalArgumentException("El combatiente ya no participa en la partida");
        }
        combatientes.put(combatienteId, actual.recibirDanio(danio));
        cerrarSiQuedaUnEquipo(MotivoFinPartida.SUPERVIVENCIA);
    }

    public void iniciarTurno(String combatienteId, Instant momento) {
        exigirEnCurso();
        Combatiente actual = combatiente(combatienteId);
        if (!actual.participa()) {
            throw new IllegalArgumentException("El turno no puede pertenecer a un combatiente eliminado");
        }
        participanteEnTurno = combatienteId;
        ultimaActividadDelTurno = Objects.requireNonNull(
                momento,
                "El instante de inicio de turno es obligatorio");
    }

    public void registrarActividad(String combatienteId, Instant momento) {
        exigirEnCurso();
        if (!Objects.equals(participanteEnTurno, combatienteId)) {
            throw new IllegalArgumentException("La actividad no pertenece al participante en turno");
        }
        ultimaActividadDelTurno = Objects.requireNonNull(
                momento,
                "El instante de actividad es obligatorio");
    }

    /** Evalúa primero la inactividad del turno y después la duración total. */
    public void evaluarLimites(Instant momento) {
        Objects.requireNonNull(momento, "El instante de evaluación es obligatorio");
        if (finalizada()) {
            return;
        }
        if (participanteEnTurno != null
                && plazoCumplido(ultimaActividadDelTurno, INACTIVIDAD_MAXIMA, momento)) {
            eliminarEquipoPorInactividad(combatiente(participanteEnTurno).equipoId());
            participanteEnTurno = null;
            ultimaActividadDelTurno = null;
            cerrarSiQuedaUnEquipo(MotivoFinPartida.INACTIVIDAD);
            if (finalizada()) {
                return;
            }
        }
        if (plazoCumplido(iniciadaEn, DURACION_MAXIMA, momento)) {
            cerrarPorTiempo();
        }
    }

    public Combatiente combatiente(String combatienteId) {
        Combatiente encontrado = combatientes.get(combatienteId);
        if (encontrado == null) {
            throw new IllegalArgumentException("El combatiente no pertenece a la partida");
        }
        return encontrado;
    }

    public boolean finalizada() {
        return resultado != null;
    }

    public Optional<ResultadoPartida> resultado() {
        return Optional.ofNullable(resultado);
    }

    private void eliminarEquipoPorInactividad(String equipoId) {
        combatientes.replaceAll((id, combatiente) ->
                equipoId.equals(combatiente.equipoId())
                        ? combatiente.perderPorInactividad()
                        : combatiente);
    }

    private void cerrarSiQuedaUnEquipo(MotivoFinPartida motivo) {
        Set<String> equipos = equiposActivos();
        if (equipos.size() == 1) {
            cerrar(equipos.iterator().next(), motivo);
        }
    }

    private Set<String> equiposActivos() {
        return combatientes.values().stream()
                .filter(Combatiente::participa)
                .map(Combatiente::equipoId)
                .collect(Collectors.toSet());
    }

    private void cerrarPorTiempo() {
        Map<String, Integer> vidaPorEquipo = combatientes.values().stream()
                .filter(Combatiente::participa)
                .collect(Collectors.groupingBy(
                        Combatiente::equipoId,
                        LinkedHashMap::new,
                        Collectors.summingInt(Combatiente::vida)));
        int mayorVida = vidaPorEquipo.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElseThrow(() -> new IllegalStateException("No quedan equipos activos"));
        List<PuntuacionEquipo> empatados = vidaPorEquipo.entrySet().stream()
                .filter(entrada -> entrada.getValue() == mayorVida)
                .map(entrada -> new PuntuacionEquipo(entrada.getKey(), entrada.getValue()))
                .toList();
        String ganador = empatados.size() == 1
                ? empatados.getFirst().equipoId()
                : criterioDesempate.desempatar(List.copyOf(empatados));
        boolean ganadorValido = empatados.stream()
                .anyMatch(equipo -> equipo.equipoId().equals(ganador));
        if (!ganadorValido) {
            throw new IllegalStateException("El criterio eligió un equipo que no estaba empatado");
        }
        cerrar(ganador, MotivoFinPartida.TIEMPO_MAXIMO);
    }

    private void cerrar(String ganadorEquipoId, MotivoFinPartida motivo) {
        resultado = new ResultadoPartida(ganadorEquipoId, motivo);
        alCerrarPartida.procesar(resultado);
    }

    private void exigirEnCurso() {
        if (finalizada()) {
            throw new PartidaFinalizadaException();
        }
    }

    private static boolean plazoCumplido(
            Instant referencia,
            Duration plazo,
            Instant momento) {
        return !momento.isBefore(referencia.plus(plazo));
    }
}
