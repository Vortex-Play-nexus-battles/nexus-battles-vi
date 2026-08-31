package nexus.combate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Partida {

    private static final AlCerrarPartida SIN_REACCION_DE_CIERRE = resultado -> { };

    private final Map<String, Combatiente> combatientes;
    private final AlCerrarPartida alCerrarPartida;
    private ResultadoPartida resultado;

    private Partida(
            List<Combatiente> participantes,
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
        this.alCerrarPartida = Objects.requireNonNull(
                alCerrarPartida,
                "El procesador de cierre es obligatorio");
    }

    public static Partida iniciar(List<Combatiente> participantes) {
        return iniciar(participantes, SIN_REACCION_DE_CIERRE);
    }

    public static Partida iniciar(
            List<Combatiente> participantes,
            AlCerrarPartida alCerrarPartida) {
        return new Partida(participantes, alCerrarPartida);
    }

    public void aplicarDanio(String combatienteId, int danio) {
        exigirEnCurso();
        Combatiente actual = combatiente(combatienteId);
        if (!actual.participa()) {
            throw new IllegalArgumentException("El combatiente ya no participa en la partida");
        }
        combatientes.put(combatienteId, actual.recibirDanio(danio));
        cerrarSiQuedaUnEquipo();
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

    private void cerrarSiQuedaUnEquipo() {
        Set<String> equipos = equiposActivos();
        if (equipos.size() == 1) {
            cerrar(equipos.iterator().next());
        }
    }

    private Set<String> equiposActivos() {
        return combatientes.values().stream()
                .filter(Combatiente::participa)
                .map(Combatiente::equipoId)
                .collect(Collectors.toSet());
    }

    private void cerrar(String ganadorEquipoId) {
        resultado = new ResultadoPartida(
                ganadorEquipoId,
                MotivoFinPartida.SUPERVIVENCIA);
        alCerrarPartida.procesar(resultado);
    }

    private void exigirEnCurso() {
        if (finalizada()) {
            throw new PartidaFinalizadaException();
        }
    }
}
