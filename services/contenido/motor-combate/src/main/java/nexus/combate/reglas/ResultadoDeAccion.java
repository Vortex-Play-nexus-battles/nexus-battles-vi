package nexus.combate.reglas;

import java.util.List;
import java.util.Map;

/**
 * Lo que devolvio una accion: que se jugo de verdad, a quien le cambio la vida,
 * que paso por el camino y el estado nuevo de TODOS los combatientes.
 *
 * @param accion          la pedida (con decision de la maquina, la decidida)
 * @param accionEjecutada la que se jugo (distinta si falto poder)
 * @param enValorBase     falto poder: el ataque se redujo a su valor base (§6.1.1)
 * @param ejecutor        quien jugo
 * @param objetivo        a quien, o nulo
 * @param tipo            clase de accion jugada
 * @param esEpica         era una epica
 * @param potenciada      epica jugada por su tipo afin
 * @param ataque          la tirada, si hubo golpe
 * @param afectados       a quien le cambio la vida
 * @param eventos         lo que paso, para narrarlo
 * @param combatientes    estado nuevo de todos
 * @param acciones        por combatiente, lo que puede jugar ahora
 * @param recargas        por combatiente, acciones en carga y turnos que faltan
 */
public record ResultadoDeAccion(String accion, String accionEjecutada, boolean enValorBase,
                                String ejecutor, String objetivo, TipoDeAccion tipo, boolean esEpica,
                                boolean potenciada, DetalleDeAtaque ataque, List<Afectado> afectados,
                                List<Evento> eventos, List<Contendiente> combatientes,
                                Map<String, List<EstadoDeAccion>> acciones,
                                Map<String, Map<String, Integer>> recargas) {

    public ResultadoDeAccion {
        afectados = List.copyOf(afectados);
        eventos = List.copyOf(eventos);
        combatientes = List.copyOf(combatientes);
        acciones = Map.copyOf(acciones);
        recargas = Map.copyOf(recargas);
    }
}
