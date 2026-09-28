package nexus.misiones.dominio;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Como ve un jugador una mision: su estado (7.8.7), por que esta bloqueada,
 * cuanto le falta si esta en curso, su ultimo reporte y los escalones que ya
 * desbloqueo (7.8.11).
 *
 * @param ultimaEjecucionId la ultima ejecucion CON reporte (Completada o
 *                          Fallida): una abandonada no tiene reporte
 * @param nueva             el jugador nunca la ha empezado
 */
public record SituacionDelJugador(
        EstadoMision estado,
        String motivoBloqueo,
        Double progreso,
        UUID ultimaEjecucionId,
        boolean nueva,
        Set<Escalon> escalonesDesbloqueados,
        boolean completadaAlgunaVez) {

    public SituacionDelJugador {
        // EnumSet y no Set.copyOf: conserva el orden de los escalones, que es
        // el orden en que se desbloquean y en que se ensenan.
        escalonesDesbloqueados = Collections.unmodifiableSet(
                escalonesDesbloqueados == null || escalonesDesbloqueados.isEmpty()
                        ? EnumSet.of(Escalon.NORMAL)
                        : EnumSet.copyOf(escalonesDesbloqueados));
    }

    /**
     * @param suyas       las ejecuciones de ESTE jugador en ESTA mision
     * @param completadas identificadores de las misiones que el jugador completo alguna vez
     * @param nombreDe    nombre de una mision por su identificador, para el motivo
     */
    public static SituacionDelJugador calcular(Mision mision, List<Ejecucion> suyas, Set<String> completadas,
                                               Function<String, String> nombreDe, Instant ahora) {
        Set<Escalon> escalones = escalonesDesbloqueados(suyas);
        boolean completada = suyas.stream().anyMatch(e -> e.estado() == EstadoEjecucion.COMPLETADA);
        UUID ultimoReporte = suyas.stream()
                .filter(e -> e.estado().tieneReporte())
                .max(Comparator.comparing(Ejecucion::terminadaEn))
                .map(Ejecucion::id)
                .orElse(null);
        boolean nueva = suyas.isEmpty();

        Optional<Ejecucion> enCurso = suyas.stream()
                .filter(e -> e.estado() == EstadoEjecucion.EN_PROGRESO)
                .findFirst();
        if (enCurso.isPresent()) {
            return new SituacionDelJugador(EstadoMision.EN_PROGRESO, null, enCurso.get().progreso(ahora),
                    ultimoReporte, nueva, escalones, completada);
        }

        List<String> faltan = mision.requisitosPrevios().stream()
                .filter(previa -> !completadas.contains(previa))
                .toList();
        if (!faltan.isEmpty()) {
            String nombres = faltan.stream()
                    .map(id -> "«" + Optional.ofNullable(nombreDe.apply(id)).orElse(id) + "»")
                    .collect(Collectors.joining(" y "));
            return new SituacionDelJugador(EstadoMision.BLOQUEADA,
                    "Completa " + nombres + " para desbloquearla.", null, ultimoReporte, nueva, escalones,
                    completada);
        }

        EstadoMision estado = suyas.stream()
                .filter(e -> e.estado().terminada())
                .max(Comparator.comparing(Ejecucion::terminadaEn))
                .map(e -> e.estado().comoEstadoDeMision())
                .orElse(EstadoMision.DISPONIBLE);
        return new SituacionDelJugador(estado, null, null, ultimoReporte, nueva, escalones, completada);
    }

    /** El escalon mas alto que el jugador puede elegir. */
    public Escalon escalonMasAlto() {
        return escalonesDesbloqueados.stream().max(Comparator.naturalOrder()).orElse(Escalon.NORMAL);
    }

    /**
     * Normal siempre (HU-REC-005: «Normal esta disponible sin requisito
     * previo»); cada uno de los demas, si el jugador completo el anterior al
     * menos una vez. Fallar despues no lo quita.
     */
    private static Set<Escalon> escalonesDesbloqueados(List<Ejecucion> suyas) {
        Set<Escalon> completados = suyas.stream()
                .filter(e -> e.estado() == EstadoEjecucion.COMPLETADA)
                .map(Ejecucion::escalon)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Escalon.class)));
        EnumSet<Escalon> desbloqueados = EnumSet.of(Escalon.NORMAL);
        for (Escalon escalon : Escalon.values()) {
            Escalon anterior = escalon.anterior();
            if (anterior != null && completados.contains(anterior)) {
                desbloqueados.add(escalon);
            }
        }
        return desbloqueados;
    }
}
