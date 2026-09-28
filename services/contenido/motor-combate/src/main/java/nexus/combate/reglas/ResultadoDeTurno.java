package nexus.combate.reglas;

import java.util.List;
import java.util.Map;

/**
 * Lo que paso al empezar el turno de un combatiente.
 *
 * @param combatiente  quien empieza su turno
 * @param afectados    a quien le cambio la vida (sangrados, sanaciones por turno)
 * @param eventos      lo que paso
 * @param combatientes estado nuevo de todos
 * @param acciones     por combatiente, lo que puede jugar ahora
 * @param recargas     por combatiente, acciones en carga y turnos que faltan
 */
public record ResultadoDeTurno(String combatiente, List<Afectado> afectados, List<Evento> eventos,
                               List<Contendiente> combatientes,
                               Map<String, List<EstadoDeAccion>> acciones,
                               Map<String, Map<String, Integer>> recargas) {

    public ResultadoDeTurno {
        afectados = List.copyOf(afectados);
        eventos = List.copyOf(eventos);
        combatientes = List.copyOf(combatientes);
        acciones = Map.copyOf(acciones);
        recargas = Map.copyOf(recargas);
    }
}
